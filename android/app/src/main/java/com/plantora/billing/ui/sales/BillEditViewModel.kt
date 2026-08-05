package com.plantora.billing.ui.sales

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.BillRepository
import com.plantora.billing.data.CheckoutItem
import com.plantora.billing.data.ProductRepository
import com.plantora.billing.R
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.i18n.UiText
import com.plantora.billing.domain.BillDetail
import com.plantora.billing.domain.DiscountType
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Product
import com.plantora.billing.ui.billing.CartLine
import com.plantora.billing.ui.billing.CartMath
import com.plantora.billing.ui.billing.CartTotals
import com.plantora.billing.ui.billing.PaymentMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owner-only bill editing: change a line's price/quantity, add or remove plants,
 * adjust the discount and payment split. The server recomputes every amount and
 * marks the bill edited. Nobody can delete a bill here (admins only, on the web).
 */
data class BillEditUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val products: List<Product> = emptyList(),
    val lines: List<CartLine> = emptyList(),
    val discountType: DiscountType = DiscountType.FLAT,
    val discountInput: String = "",
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val cashInput: String = "",
    val dueInput: String = "",
    val remarks: String = "",
    val customerPhone: String? = null,
    val showAddPicker: Boolean = false,
    val saving: Boolean = false,
    val saveError: UiText? = null,
    val saved: Boolean = false,
) {
    val discountValue: Money get() = Money.parse(discountInput.ifBlank { "0" })
    val totals: CartTotals get() = CartMath.totals(lines, discountType, discountValue)
    val isEmpty: Boolean get() = lines.isEmpty()
    val payment: Pair<Money, Money>
        get() = CartMath.paymentSplit(totals.total, paymentMode, Money.parse(cashInput.ifBlank { "0" }), Money.parse(dueInput.ifBlank { "0" }))
}

@HiltViewModel
class BillEditViewModel @Inject constructor(
    private val billRepo: BillRepository,
    private val productRepo: ProductRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val billId: String = checkNotNull(savedStateHandle["billId"])

    private val _ui = MutableStateFlow(BillEditUiState())
    val ui: StateFlow<BillEditUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val productsResult = runCatching { productRepo.list() }
            runCatching { billRepo.detail(billId) }
                .onSuccess { detail ->
                    val catalog = productsResult.getOrDefault(emptyList())
                    _ui.update { seedFrom(detail, catalog) }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    private fun seedFrom(detail: BillDetail, catalog: List<Product>): BillEditUiState {
        val lines = detail.items.mapNotNull { item ->
            val pid = item.productId ?: return@mapNotNull null
            val product = catalog.find { it.id == pid }
                ?: Product(id = pid, name = item.productName, category = null, retailPrice = item.unitPrice, photoUrl = null, isActive = true)
            // Each saved item becomes its OWN line (unique id), so editing or removing
            // one "Banana tree" line never affects another line of the same product.
            CartLine(id = java.util.UUID.randomUUID().toString(), product = product, qtyInput = item.quantity.toString(), priceInput = item.unitPrice.toInput())
        }
        val mode = when {
            detail.cashAmount.isPositive() && detail.upiAmount.isPositive() -> PaymentMode.SPLIT
            detail.upiAmount.isPositive() && !detail.cashAmount.isPositive() -> PaymentMode.UPI
            else -> PaymentMode.CASH
        }
        return BillEditUiState(
            loading = false,
            products = catalog,
            lines = lines,
            discountType = detail.discountType,
            discountInput = if (detail.discountValue.isPositive()) detail.discountValue.toWire() else "",
            paymentMode = mode,
            cashInput = if (detail.cashAmount.isPositive()) detail.cashAmount.toWire() else "",
            dueInput = if (detail.dueAmount.isPositive()) detail.dueAmount.toWire() else "",
            remarks = detail.remarks.orEmpty(),
            customerPhone = detail.customerPhone,
        )
    }

    fun setQuantity(lineId: String, quantity: Int) = _ui.update { state ->
        val lines = if (quantity <= 0) state.lines.filterNot { it.id == lineId }
        else state.lines.map { if (it.id == lineId) it.copy(qtyInput = quantity.toString()) else it }
        state.copy(lines = lines)
    }

    fun setUnitPrice(lineId: String, priceInput: String) = _ui.update { state ->
        state.copy(lines = state.lines.map { if (it.id == lineId) it.copy(priceInput = priceInput) else it })
    }

    fun removeLine(lineId: String) = setQuantity(lineId, 0)

    // Always a NEW separate line (same product can appear several times at different
    // prices) — matches the billing screen and keeps each line independently editable.
    fun addProduct(product: Product) = _ui.update { state ->
        val line = CartLine(
            id = java.util.UUID.randomUUID().toString(),
            product = product,
            qtyInput = "1",
            priceInput = product.retailPrice.toInput(),
        )
        state.copy(lines = state.lines + line, showAddPicker = false)
    }

    fun openAddPicker() = _ui.update { it.copy(showAddPicker = true) }
    fun closeAddPicker() = _ui.update { it.copy(showAddPicker = false) }
    fun setDiscountType(type: DiscountType) = _ui.update { it.copy(discountType = type) }
    fun setDiscountInput(v: String) = _ui.update { it.copy(discountInput = v) }
    fun setPaymentMode(mode: PaymentMode) = _ui.update { it.copy(paymentMode = mode) }
    fun setCashInput(v: String) = _ui.update { it.copy(cashInput = v) }
    fun setDueInput(v: String) = _ui.update { it.copy(dueInput = v) }
    fun setRemarks(v: String) = _ui.update { it.copy(remarks = v) }
    fun dismissSaveError() = _ui.update { it.copy(saveError = null) }

    fun save() {
        val state = _ui.value
        if (state.isEmpty || state.saving) return
        val (cash, upi) = state.payment
        val due = Money.parse(state.dueInput.ifBlank { "0" }).let { if (it > state.totals.total) state.totals.total else it }
        if (due.isPositive() && (state.customerPhone?.filter { it.isDigit() }?.length ?: 0) < 10) {
            _ui.update { it.copy(saveError = UiText.res(R.string.vm_due_no_phone)) }
            return
        }
        _ui.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            runCatching {
                billRepo.editBill(
                    id = billId,
                    items = state.lines.map { CheckoutItem(it.product.id, it.quantity, it.unitPrice) },
                    discountType = state.discountType,
                    discountValue = state.discountValue,
                    cash = cash,
                    upi = upi,
                    due = due,
                    remarks = state.remarks,
                )
            }
                .onSuccess { _ui.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _ui.update { it.copy(saving = false, saveError = UiText.err(e, R.string.err_save_changes)) } }
        }
    }
}
