package com.plantora.billing.ui.borrowings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.BorrowingRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.Borrowing
import com.plantora.billing.domain.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BorrowMode { CASH, UPI, SPLIT }
enum class BorrowFilter(val wire: String) { ALL("all"), OPEN("open"), PAID("paid") }

/** Resolve a total + mode + cash-part into a (cash, upi) pair that sums to the total. */
private fun split(total: Money, mode: BorrowMode, splitCash: String): Pair<Money, Money> = when (mode) {
    BorrowMode.CASH -> total to Money.ZERO
    BorrowMode.UPI -> Money.ZERO to total
    BorrowMode.SPLIT -> {
        val cash = Money.parse(splitCash).let { if (it > total) total else it }
        cash to (total - cash)
    }
}

/** Add-borrowing form. */
data class AddEditor(
    val name: String = "",
    val phone: String = "",
    val amount: String = "",
    val mode: BorrowMode = BorrowMode.CASH,
    val splitCash: String = "",
    val remarks: String = "",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val total: Money get() = Money.parse(amount)
    val canSave: Boolean get() = name.isNotBlank() && total.isPositive() && !saving &&
        (mode != BorrowMode.SPLIT || Money.parse(splitCash) <= total)
}

/** Mark-paid form for a specific borrowing. */
data class PayEditor(
    val borrowing: Borrowing,
    val mode: BorrowMode = BorrowMode.CASH,
    val splitCash: String = "",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = !saving &&
        (mode != BorrowMode.SPLIT || Money.parse(splitCash) <= borrowing.amount)
}

data class BorrowingsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val filter: BorrowFilter = BorrowFilter.ALL,
    val items: List<Borrowing> = emptyList(),
    val totalOutstanding: Money = Money.ZERO,
    val addEditor: AddEditor? = null,
    val payEditor: PayEditor? = null,
    val message: String? = null,
)

@HiltViewModel
class BorrowingsViewModel @Inject constructor(
    private val repo: BorrowingRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(BorrowingsUiState())
    val ui: StateFlow<BorrowingsUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.list(_ui.value.filter.wire) }
                .onSuccess { r -> _ui.update { it.copy(loading = false, items = r.items, totalOutstanding = r.totalOutstanding) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun setFilter(f: BorrowFilter) { _ui.update { it.copy(filter = f) }; load() }

    // ── Add editor ──
    fun openAdd() = _ui.update { it.copy(addEditor = AddEditor()) }
    fun closeAdd() = _ui.update { it.copy(addEditor = null) }
    fun setName(v: String) = _ui.update { it.copy(addEditor = it.addEditor?.copy(name = v, error = null)) }
    fun setPhone(v: String) = _ui.update { it.copy(addEditor = it.addEditor?.copy(phone = v, error = null)) }
    fun setAmount(v: String) = _ui.update { it.copy(addEditor = it.addEditor?.copy(amount = v, error = null)) }
    fun setRemarks(v: String) = _ui.update { it.copy(addEditor = it.addEditor?.copy(remarks = v)) }
    fun setAddMode(m: BorrowMode) = _ui.update {
        val ed = it.addEditor ?: return@update it
        val seeded = if (m == BorrowMode.SPLIT && ed.splitCash.isBlank()) ed.copy(mode = m, splitCash = ed.total.toInput()) else ed.copy(mode = m)
        it.copy(addEditor = seeded.copy(error = null))
    }
    fun setAddSplitCash(v: String) = _ui.update { it.copy(addEditor = it.addEditor?.copy(splitCash = v, error = null)) }

    fun saveAdd() {
        val e = _ui.value.addEditor ?: return
        if (!e.canSave) return
        _ui.update { it.copy(addEditor = e.copy(saving = true, error = null)) }
        val (cash, upi) = split(e.total, e.mode, e.splitCash)
        viewModelScope.launch {
            runCatching { repo.create(e.name, e.phone, e.total, cash, upi, e.remarks) }
                .onSuccess { _ui.update { it.copy(addEditor = null, message = "Borrowing added.") }; load() }
                .onFailure { err -> _ui.update { it.copy(addEditor = e.copy(saving = false, error = friendlyError(err))) } }
        }
    }

    // ── Pay editor ──
    fun openPay(b: Borrowing) = _ui.update { it.copy(payEditor = PayEditor(borrowing = b, splitCash = "")) }
    fun closePay() = _ui.update { it.copy(payEditor = null) }
    fun setPayMode(m: BorrowMode) = _ui.update {
        val ed = it.payEditor ?: return@update it
        val seeded = if (m == BorrowMode.SPLIT && ed.splitCash.isBlank()) ed.copy(mode = m, splitCash = ed.borrowing.amount.toInput()) else ed.copy(mode = m)
        it.copy(payEditor = seeded.copy(error = null))
    }
    fun setPaySplitCash(v: String) = _ui.update { it.copy(payEditor = it.payEditor?.copy(splitCash = v, error = null)) }

    fun savePay() {
        val e = _ui.value.payEditor ?: return
        if (!e.canSave) return
        _ui.update { it.copy(payEditor = e.copy(saving = true, error = null)) }
        val (cash, upi) = split(e.borrowing.amount, e.mode, e.splitCash)
        viewModelScope.launch {
            runCatching { repo.pay(e.borrowing.id, cash, upi) }
                .onSuccess { _ui.update { it.copy(payEditor = null, message = "Marked paid.") }; load() }
                .onFailure { err -> _ui.update { it.copy(payEditor = e.copy(saving = false, error = friendlyError(err))) } }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repo.delete(id) }
                .onSuccess { _ui.update { it.copy(message = "Deleted.") }; load() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
