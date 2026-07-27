package com.plantora.billing.ui.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.R
import com.plantora.billing.data.BillRepository
import com.plantora.billing.data.CheckoutItem
import com.plantora.billing.data.CheckoutRequest
import com.plantora.billing.data.ProductRepository
import com.plantora.billing.data.local.HeldBill
import com.plantora.billing.data.local.HeldBillStore
import com.plantora.billing.data.local.HeldLine
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.Bill
import com.plantora.billing.domain.DiscountType
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Product
import com.plantora.billing.i18n.UiText
import com.plantora.billing.print.PrinterController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class CheckoutPhase { IDLE, SUBMITTING }

enum class PrintPhase { IDLE, CONNECTING, PRINTING, DONE, FAILED }

data class BillingUiState(
    val productsLoading: Boolean = true,
    val products: List<Product> = emptyList(),
    val productsError: UiText? = null,
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
    val checkoutError: UiText? = null,
    val success: Bill? = null,
    val printPhase: PrintPhase = PrintPhase.IDLE,
    val printMessage: UiText? = null,
    val categoryFilter: String? = null,
    val quickAdd: QuickAddState? = null,
    val toast: UiText? = null,
    val businessUpi: String? = null,
    val businessName: String = "",
    // Whether the review sheet is open. Owned by the VM (not local screen state)
    // so it can't desync from the cart: set true on every add, false on close.
    // Surviving process death alongside the cart means a returning user never
    // gets a spurious open, and an add always opens it.
    val showReview: Boolean = false,
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
    // Price may be ₹0 (e.g. a free item) — only a blank or negative price blocks it.
    val canSave: Boolean get() = name.isNotBlank() && price.isNotBlank() && !Money.parse(price).isNegative() && !saving
}

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val productRepo: ProductRepository,
    private val billRepo: BillRepository,
    private val printer: PrinterController,
    private val heldStore: HeldBillStore,
    session: com.plantora.billing.data.SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(BillingUiState())
    val ui: StateFlow<BillingUiState> = _ui.asStateFlow()

    /** Parked bills stored on this device (for the "Held bills" list). */
    val heldBills: StateFlow<List<HeldBill>> =
        heldStore.heldBills.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                .onFailure { e -> _ui.update { it.copy(productsLoading = false, productsError = UiText.err(e, R.string.err_generic)) } }
        }
    }

    fun onQueryChange(q: String) = _ui.update { it.copy(query = q) }

    fun setCategoryFilter(c: String?) = _ui.update { it.copy(categoryFilter = c) }

    fun showToast(message: String) = _ui.update { it.copy(toast = UiText.of(message)) }

    /**
     * Voice → the closest plant in THIS shop's catalog, always. The recognizer
     * returns several alternative phrases; we score every one against the product
     * names and add the single nearest plant. The mic is deliberately restricted
     * to the catalog: whatever is spoken snaps to a real product name — no stray
     * words, no plain-text search.
     */
    fun onVoiceTranscript(alternatives: List<String>) {
        val products = _ui.value.products
        if (products.isEmpty()) {
            _ui.update { it.copy(toast = UiText.res(R.string.vm_add_products_voice)) }
            return
        }
        val names = products.map { it.name }
        var best: com.plantora.billing.ui.billing.voice.PhoneticMatcher.Match? = null
        for (alt in alternatives) {
            val m = com.plantora.billing.ui.billing.voice.PhoneticMatcher.findClosest(alt, names)
            if (m != null && (best == null || m.score > best!!.score)) best = m
        }
        val product = best?.let { mm -> products.find { it.name == mm.candidate } }
            ?: products.first()
        addProduct(product)
        _ui.update { it.copy(query = "", toast = UiText.res(R.string.vm_added_to_cart, product.name)) }
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
                    addLine(product, form.quantity)
                    _ui.update { it.copy(toast = UiText.res(R.string.vm_added, product.name)) }
                }
                .onFailure { e -> _ui.update { it.copy(quickAdd = form.copy(saving = false, error = friendlyError(e, "Couldn't add the item."))) } }
        }
    }

    fun dismissToast() = _ui.update { it.copy(toast = null) }

    /**
     * Always appends a NEW line — tapping a product that's already in the cart does
     * NOT increment the existing line. Same plant, different size = different price,
     * so each tap is kept separate (the owner edits each line's price independently).
     * Bumps [cartPulse] so the screen pops the review sheet on every add.
     */
    fun addProduct(product: Product) = addLine(product, quantity = 1)

    private fun addLine(product: Product, quantity: Int) = _ui.update { state ->
        val line = CartLine(
            id = UUID.randomUUID().toString(),
            product = product,
            quantity = quantity.coerceAtLeast(1),
            unitPrice = product.retailPrice,
        )
        // Open the review on every add; also clear the search box so the next
        // product is searched from empty (no leftover "rose" to backspace).
        state.copy(lines = state.lines + line, showReview = true, query = "")
    }

    /** Open the review sheet from the cart bar. */
    fun openReview() = _ui.update { it.copy(showReview = true) }

    /** Close the review (✕ / Add item). Cart is untouched; next add reopens it. */
    fun dismissReview() = _ui.update { it.copy(showReview = false) }

    fun setQuantity(lineId: String, quantity: Int) = _ui.update { state ->
        val newLines = if (quantity <= 0) {
            state.lines.filterNot { it.id == lineId }
        } else {
            state.lines.map { if (it.id == lineId) it.copy(quantity = quantity) else it }
        }
        state.copy(lines = newLines)
    }

    fun setUnitPrice(lineId: String, priceInput: String) = _ui.update { state ->
        val price = Money.parse(priceInput.ifBlank { "0" })
        state.copy(lines = state.lines.map { if (it.id == lineId) it.copy(unitPrice = price) else it })
    }

    fun removeLine(lineId: String) = setQuantity(lineId, 0)

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
                showReview = false,
            )
        }
    }

    /**
     * Park the current cart so a ready customer can be billed first. Saves a
     * device-local snapshot of the whole bill (lines, discount, payment, customer),
     * then empties the cart. Resume it later from "Held bills".
     */
    fun holdBill() {
        val s = _ui.value
        if (s.isCartEmpty) return
        val held = HeldBill(
            id = UUID.randomUUID().toString(),
            savedAt = System.currentTimeMillis(),
            label = s.customerName.ifBlank { "Walk-in customer" },
            itemCount = s.itemCount,
            total = s.totals.total.toWire(),
            lines = s.lines.map {
                HeldLine(
                    lineId = it.id,
                    productId = it.product.id,
                    productName = it.product.name,
                    productCategory = it.product.category,
                    productPhotoUrl = it.product.photoUrl,
                    productRetailPrice = it.product.retailPrice.toWire(),
                    quantity = it.quantity,
                    unitPrice = it.unitPrice.toWire(),
                )
            },
            discountType = s.discountType.name,
            discountInput = s.discountInput,
            paymentMode = s.paymentMode.name,
            cashInput = s.cashInput,
            dueInput = s.dueInput,
            customerName = s.customerName,
            customerPhone = s.customerPhone,
            remarks = s.remarks,
        )
        viewModelScope.launch { heldStore.save(held) }
        clearCart()
        _ui.update { it.copy(toast = UiText.res(R.string.vm_bill_held)) }
    }

    /**
     * Load a parked bill back into the cart. If a cart is already in progress it is
     * parked first (never silently discarded), then the chosen held bill is restored
     * and removed from the held list.
     */
    fun resumeBill(bill: HeldBill) {
        if (!_ui.value.isCartEmpty) holdBill()
        idempotencyKey = UUID.randomUUID().toString()
        val lines = bill.lines.map { hl ->
            CartLine(
                id = hl.lineId,
                product = Product(
                    id = hl.productId,
                    name = hl.productName,
                    category = hl.productCategory,
                    retailPrice = Money.parse(hl.productRetailPrice),
                    photoUrl = hl.productPhotoUrl,
                    isActive = true,
                ),
                quantity = hl.quantity,
                unitPrice = Money.parse(hl.unitPrice),
            )
        }
        _ui.update {
            it.copy(
                lines = lines,
                discountType = runCatching { DiscountType.valueOf(bill.discountType) }.getOrDefault(DiscountType.FLAT),
                discountInput = bill.discountInput,
                paymentMode = runCatching { PaymentMode.valueOf(bill.paymentMode) }.getOrDefault(PaymentMode.CASH),
                cashInput = bill.cashInput,
                dueInput = bill.dueInput,
                customerName = bill.customerName,
                customerPhone = bill.customerPhone,
                remarks = bill.remarks,
                checkoutError = null,
                showReview = true,
            )
        }
        viewModelScope.launch { heldStore.remove(bill.id) }
    }

    fun discardHeld(id: String) {
        viewModelScope.launch { heldStore.remove(id) }
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

        // A due means money owed later — we must be able to reach the customer, so
        // their phone number is compulsory whenever any amount is left unpaid.
        if (due.isPositive() && state.customerPhone.filter { it.isDigit() }.length < 10) {
            _ui.update { it.copy(checkoutError = UiText.res(R.string.vm_due_phone_required)) }
            return
        }

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
                            checkoutError = UiText.err(e, R.string.err_save_bill),
                        )
                    }
                }
        }
    }

    /** Print the just-saved bill (fetches full detail for shop header). */
    fun printSuccessBill() {
        val billId = _ui.value.success?.id ?: return
        _ui.update { it.copy(printPhase = PrintPhase.CONNECTING, printMessage = UiText.res(R.string.vm_print_connecting)) }
        viewModelScope.launch {
            // Reflect the live connection state: "Connecting…" until the printer
            // links up, then "Printing…" while bytes are sent.
            val statusJob = launch {
                printer.status.collect { st ->
                    when (st) {
                        is com.plantora.billing.print.PrinterStatus.Connecting ->
                            _ui.update { it.copy(printPhase = PrintPhase.CONNECTING, printMessage = UiText.res(R.string.vm_print_connecting)) }
                        is com.plantora.billing.print.PrinterStatus.Connected ->
                            _ui.update { it.copy(printPhase = PrintPhase.PRINTING, printMessage = UiText.res(R.string.vm_print_printing)) }
                        else -> {}
                    }
                }
            }
            val result = runCatching { billRepo.detail(billId) }
                .mapCatching { detail -> printer.printBill(detail).getOrThrow() }
            statusJob.cancel()
            result
                .onSuccess { _ui.update { it.copy(printPhase = PrintPhase.DONE, printMessage = UiText.res(R.string.vm_print_printed)) } }
                .onFailure { e ->
                    _ui.update {
                        it.copy(printPhase = PrintPhase.FAILED, printMessage = e.message?.let(UiText::of) ?: UiText.err(e, R.string.vm_print_failed))
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
