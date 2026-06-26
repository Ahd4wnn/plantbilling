package com.plantora.billing.ui.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.BillRepository
import com.plantora.billing.data.CheckoutItem
import com.plantora.billing.data.CheckoutRequest
import com.plantora.billing.data.ProductRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.Bill
import com.plantora.billing.domain.DiscountType
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Product
import com.plantora.billing.print.PrinterController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class CheckoutPhase { IDLE, SUBMITTING }

enum class PrintPhase { IDLE, CONNECTING, PRINTING, DONE, FAILED }

data class BillingUiState(
    val productsLoading: Boolean = true,
    val products: List<Product> = emptyList(),
    val productsError: String? = null,
    val query: String = "",
    val lines: List<CartLine> = emptyList(),
    val discountType: DiscountType = DiscountType.FLAT,
    val discountInput: String = "",
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val cashInput: String = "",
    val dueInput: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val remarks: String = "",
    val checkout: CheckoutPhase = CheckoutPhase.IDLE,
    val checkoutError: String? = null,
    val success: Bill? = null,
    val printPhase: PrintPhase = PrintPhase.IDLE,
    val printMessage: String? = null,
    val categoryFilter: String? = null,
    val quickAdd: QuickAddState? = null,
    val toast: String? = null,
    val businessUpi: String? = null,
    val businessName: String = "",
) {
    val discountValue: Money get() = Money.parse(discountInput.ifBlank { "0" })
    val totals: CartTotals get() = CartMath.totals(lines, discountType, discountValue)
    val itemCount: Int get() = lines.sumOf { it.quantity }
    val isCartEmpty: Boolean get() = lines.isEmpty()

    val payment: Pair<Money, Money>
        get() = CartMath.paymentSplit(totals.total, paymentMode, Money.parse(cashInput.ifBlank { "0" }), Money.parse(dueInput.ifBlank { "0" }))

    val categories: List<String>
        get() = products.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }.distinct().sorted()

    val filteredProducts: List<Product>
        get() = products
            .filter { categoryFilter == null || it.category == categoryFilter }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
}

/** Quick-add custom item form (creates a "Quick Add" product, then carts it). */
data class QuickAddState(
    val name: String = "",
    val price: String = "",
    val quantity: Int = 1,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = name.isNotBlank() && Money.parse(price).isPositive() && !saving
}

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val productRepo: ProductRepository,
    private val billRepo: BillRepository,
    private val printer: PrinterController,
    session: com.plantora.billing.data.SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(BillingUiState())
    val ui: StateFlow<BillingUiState> = _ui.asStateFlow()

    // Reused across retries of the same cart so double-taps never duplicate a bill.
    private var idempotencyKey: String = UUID.randomUUID().toString()

    init {
        // Shop UPI ID + name for the payment QR code (from the signed-in session).
        (session.state.value as? com.plantora.billing.data.AuthState.Authenticated)?.user?.let { u ->
            _ui.update { it.copy(businessUpi = u.businessUpi, businessName = u.displayShop) }
        }
        loadProducts()
    }

    fun loadProducts() {
        _ui.update { it.copy(productsLoading = true, productsError = null) }
        viewModelScope.launch {
            runCatching { productRepo.list() }
                .onSuccess { list -> _ui.update { it.copy(productsLoading = false, products = list) } }
                .onFailure { e -> _ui.update { it.copy(productsLoading = false, productsError = friendlyError(e)) } }
        }
    }

    fun onQueryChange(q: String) = _ui.update { it.copy(query = q) }

    fun setCategoryFilter(c: String?) = _ui.update { it.copy(categoryFilter = c) }

    fun showToast(message: String) = _ui.update { it.copy(toast = message) }

    /**
     * Voice guesses → closest available plant added to cart. The recognizer
     * returns several alternative phrases; we score every one against the
     * product names and pick the single best phonetic match, so unclear or
     * heavily-accented speech still snaps to a real plant. Falls back to a text
     * search only when nothing is even close.
     */
    fun onVoiceTranscript(alternatives: List<String>) {
        val products = _ui.value.products
        val names = products.map { it.name }
        if (names.isEmpty() || alternatives.isEmpty()) {
            _ui.update { it.copy(query = alternatives.firstOrNull().orEmpty()) }
            return
        }
        var best: com.plantora.billing.ui.billing.voice.PhoneticMatcher.Match? = null
        for (alt in alternatives) {
            val m = com.plantora.billing.ui.billing.voice.PhoneticMatcher.findBestMatch(alt, names)
            if (m != null && (best == null || m.score > best!!.score)) best = m
        }
        val product = best?.let { mm -> products.find { it.name == mm.candidate } }
        if (product != null) {
            addProduct(product)
            _ui.update { it.copy(query = "", toast = "Added ${product.name}") }
        } else {
            _ui.update { it.copy(query = alternatives.first(), toast = "No close match — showing results") }
        }
    }

    // ── Quick Add ──
    fun openQuickAdd() = _ui.update { it.copy(quickAdd = QuickAddState()) }
    fun closeQuickAdd() = _ui.update { it.copy(quickAdd = null) }
    fun setQuickAddName(v: String) = _ui.update { it.copy(quickAdd = it.quickAdd?.copy(name = v, error = null)) }
    fun setQuickAddPrice(v: String) = _ui.update { it.copy(quickAdd = it.quickAdd?.copy(price = v, error = null)) }
    fun setQuickAddQuantity(q: Int) = _ui.update { it.copy(quickAdd = it.quickAdd?.copy(quantity = q.coerceAtLeast(1))) }

    fun saveQuickAdd() {
        val form = _ui.value.quickAdd ?: return
        if (!form.canSave) return
        _ui.update { it.copy(quickAdd = form.copy(saving = true, error = null)) }
        viewModelScope.launch {
            runCatching { productRepo.create(form.name, Money.parse(form.price), category = "Quick Add") }
                .onSuccess { product ->
                    _ui.update { state ->
                        val withProduct = state.copy(products = listOf(product) + state.products, quickAdd = null)
                        withProduct
                    }
                    repeat(form.quantity) { addProduct(product) }
                    _ui.update { it.copy(toast = "Added ${product.name}") }
                }
                .onFailure { e -> _ui.update { it.copy(quickAdd = form.copy(saving = false, error = friendlyError(e, "Couldn't add the item."))) } }
        }
    }

    fun dismissToast() = _ui.update { it.copy(toast = null) }

    fun addProduct(product: Product) = _ui.update { state ->
        val existing = state.lines.find { it.product.id == product.id }
        val newLines = if (existing != null) {
            state.lines.map { if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it }
        } else {
            state.lines + CartLine(product, quantity = 1, unitPrice = product.retailPrice)
        }
        state.copy(lines = newLines)
    }

    fun setQuantity(productId: String, quantity: Int) = _ui.update { state ->
        val newLines = if (quantity <= 0) {
            state.lines.filterNot { it.product.id == productId }
        } else {
            state.lines.map { if (it.product.id == productId) it.copy(quantity = quantity) else it }
        }
        state.copy(lines = newLines)
    }

    fun setUnitPrice(productId: String, priceInput: String) = _ui.update { state ->
        val price = Money.parse(priceInput.ifBlank { "0" })
        state.copy(lines = state.lines.map { if (it.product.id == productId) it.copy(unitPrice = price) else it })
    }

    fun removeLine(productId: String) = setQuantity(productId, 0)

    /** Empty the cart and reset all bill inputs, keeping the loaded catalog. */
    fun clearCart() {
        idempotencyKey = UUID.randomUUID().toString()
        _ui.update {
            it.copy(
                lines = emptyList(),
                discountType = DiscountType.FLAT,
                discountInput = "",
                paymentMode = PaymentMode.CASH,
                cashInput = "",
                dueInput = "",
                customerName = "",
                customerPhone = "",
                remarks = "",
                checkoutError = null,
            )
        }
    }

    fun setDiscountType(type: DiscountType) = _ui.update { it.copy(discountType = type) }
    fun setDiscountInput(v: String) = _ui.update { it.copy(discountInput = v) }
    fun setPaymentMode(mode: PaymentMode) = _ui.update { it.copy(paymentMode = mode) }
    fun setCashInput(v: String) = _ui.update { it.copy(cashInput = v) }
    fun setDueInput(v: String) = _ui.update { it.copy(dueInput = v) }
    fun setCustomerName(v: String) = _ui.update { it.copy(customerName = v) }
    fun setCustomerPhone(v: String) = _ui.update { it.copy(customerPhone = v) }
    fun setRemarks(v: String) = _ui.update { it.copy(remarks = v) }

    fun checkout() {
        val state = _ui.value
        if (state.isCartEmpty || state.checkout == CheckoutPhase.SUBMITTING) return
        val (cash, upi) = state.payment
        val due = Money.parse(state.dueInput.ifBlank { "0" }).let { if (it > state.totals.total) state.totals.total else it }

        _ui.update { it.copy(checkout = CheckoutPhase.SUBMITTING, checkoutError = null) }
        viewModelScope.launch {
            val req = CheckoutRequest(
                idempotencyKey = idempotencyKey,
                items = state.lines.map { CheckoutItem(it.product.id, it.quantity, it.unitPrice) },
                discountType = state.discountType,
                discountValue = state.discountValue,
                cashAmount = cash,
                upiAmount = upi,
                dueAmount = due,
                remarks = state.remarks,
                customerName = state.customerName,
                customerPhone = state.customerPhone,
            )
            runCatching { billRepo.checkout(req) }
                .onSuccess { bill -> _ui.update { it.copy(checkout = CheckoutPhase.IDLE, success = bill) } }
                .onFailure { e ->
                    _ui.update {
                        it.copy(
                            checkout = CheckoutPhase.IDLE,
                            checkoutError = friendlyError(e, "Couldn't save the bill. Please try again."),
                        )
                    }
                }
        }
    }

    /** Print the just-saved bill (fetches full detail for shop header). */
    fun printSuccessBill() {
        val billId = _ui.value.success?.id ?: return
        _ui.update { it.copy(printPhase = PrintPhase.CONNECTING, printMessage = "Connecting to printer…") }
        viewModelScope.launch {
            // Reflect the live connection state: "Connecting…" until the printer
            // links up, then "Printing…" while bytes are sent.
            val statusJob = launch {
                printer.status.collect { st ->
                    when (st) {
                        is com.plantora.billing.print.PrinterStatus.Connecting ->
                            _ui.update { it.copy(printPhase = PrintPhase.CONNECTING, printMessage = "Connecting to printer…") }
                        is com.plantora.billing.print.PrinterStatus.Connected ->
                            _ui.update { it.copy(printPhase = PrintPhase.PRINTING, printMessage = "Printing…") }
                        else -> {}
                    }
                }
            }
            val result = runCatching { billRepo.detail(billId) }
                .mapCatching { detail -> printer.printBill(detail).getOrThrow() }
            statusJob.cancel()
            result
                .onSuccess { _ui.update { it.copy(printPhase = PrintPhase.DONE, printMessage = "Printed.") } }
                .onFailure { e ->
                    _ui.update {
                        it.copy(printPhase = PrintPhase.FAILED, printMessage = friendlyError(e, e.message ?: "Printing failed."))
                    }
                }
        }
    }

    fun startNewBill() {
        idempotencyKey = UUID.randomUUID().toString()
        _ui.update {
            BillingUiState(
                productsLoading = false,
                products = it.products,
                businessUpi = it.businessUpi,
                businessName = it.businessName,
            )
        }
    }

    fun dismissCheckoutError() = _ui.update { it.copy(checkoutError = null) }
}
