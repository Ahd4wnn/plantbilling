package com.plantora.billing.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.AuthState
import com.plantora.billing.data.BillRepository
import com.plantora.billing.data.ExpenseRepository
import com.plantora.billing.data.SalespersonRepository
import com.plantora.billing.data.SessionRepository
import com.plantora.billing.data.local.AppPreferences
import com.plantora.billing.R
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.i18n.UiText
import com.plantora.billing.domain.BillListEntry
import com.plantora.billing.domain.DaySummary
import com.plantora.billing.domain.ExpenseCategory
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Role
import com.plantora.billing.domain.Salesperson
import com.plantora.billing.domain.toApiDate
import com.plantora.billing.domain.todayInShopZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val PAGE = 20

/** State for the add/edit expense sheet. id == null → create. */
data class ExpenseEditor(
    val id: String? = null,
    val amount: String = "",
    /** Selected expense category id. Required to save (replaces free-text reason). */
    val categoryId: String? = null,
    val note: String = "",
    /** "cash" (out of the drawer) or "upi". */
    val paymentMethod: String = "cash",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = Money.parse(amount).isPositive() && categoryId != null && !saving
}

/** A salesperson and their sales total for the selected day (leaderboard row). */
data class StaffSales(val salesperson: Salesperson, val sales: Money)

data class SalesUiState(
    val date: LocalDate = todayInShopZone(),
    val isOwner: Boolean = false,
    val staff: List<Salesperson> = emptyList(),
    val staffSales: List<StaffSales> = emptyList(),
    val selectedStaffId: String? = null,
    val summaryLoading: Boolean = true,
    val summary: DaySummary? = null,
    /** Device preference: show cash in hand as a running all-time total. */
    val cashInHandCumulative: Boolean = false,
    val error: String? = null,
    val bills: List<BillListEntry> = emptyList(),
    val billsLoading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val expenseEditor: ExpenseEditor? = null,
    /** The shop's expense categories, for the add/edit picker. */
    val expenseCategories: List<ExpenseCategory> = emptyList(),
    /** Bill-number search box (digits only). Non-blank → the list shows search results
     *  across all history instead of the selected day. */
    val searchNo: String = "",
    val message: UiText? = null,
) {
    val isToday: Boolean get() = date == todayInShopZone()
    val isSearching: Boolean get() = searchNo.isNotBlank()
    /** Selected salesperson's email, or null for "all staff" (localized in the UI). */
    val selectedStaffEmail: String?
        get() = staff.find { it.id == selectedStaffId }?.email
}

@HiltViewModel
class SalesViewModel @Inject constructor(
    private val billRepo: BillRepository,
    private val expenseRepo: ExpenseRepository,
    private val salespersonRepo: SalespersonRepository,
    session: SessionRepository,
    prefs: AppPreferences,
) : ViewModel() {

    private val _ui = MutableStateFlow(SalesUiState())
    val ui: StateFlow<SalesUiState> = _ui.asStateFlow()

    init {
        val owner = (session.state.value as? AuthState.Authenticated)?.user?.role == Role.MANAGER
        _ui.update { it.copy(isOwner = owner) }
        // Keep the running-cash-in-hand display preference live.
        viewModelScope.launch {
            prefs.cashInHandCumulative.collect { v -> _ui.update { it.copy(cashInHandCumulative = v) } }
        }
        if (owner) loadStaff()
        // NOTE: the first/refresh load() is driven by the screen's resume effect
        // (see SalesScreen). The bottom nav saves/restores this screen's state, so
        // relying on init alone would show stale data — a bill made on the Bill tab
        // wouldn't appear until the VM was recreated. Reloading on resume keeps the
        // sales list and summary current every time the tab is opened.
    }

    private fun loadStaff() {
        viewModelScope.launch {
            runCatching { salespersonRepo.list() }
                .onSuccess { list -> _ui.update { it.copy(staff = list) }; refreshLeaderboard() }
        }
    }

    /**
     * Rank salespeople by their sales for the selected day so the owner can see who
     * sold the most. Fans out one day-summary request per staff member (small N).
     */
    private fun refreshLeaderboard() {
        val state = _ui.value
        if (!state.isOwner || state.staff.isEmpty()) return
        val date = state.date.toApiDate()
        val staff = state.staff
        viewModelScope.launch {
            val rows = staff.map { sp ->
                val sales = runCatching { billRepo.summary(date, sp.id).totalSales }.getOrDefault(Money.ZERO)
                StaffSales(sp, sales)
            }.sortedByDescending { it.sales }
            _ui.update { it.copy(staffSales = rows.filter { r -> r.sales.isPositive() }) }
        }
    }

    fun load() {
        // A bill-number search owns the list while it's active; the tab's resume refresh
        // must not wipe it. The search results are (re)driven by setSearchNo instead.
        if (_ui.value.isSearching) { runSearch(); return }
        val date = _ui.value.date.toApiDate()
        val staff = _ui.value.selectedStaffId
        _ui.update { it.copy(summaryLoading = true, billsLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { billRepo.summary(date, staff) }
                .onSuccess { s -> _ui.update { it.copy(summaryLoading = false, summary = s) } }
                .onFailure { e -> _ui.update { it.copy(summaryLoading = false, error = friendlyError(e)) } }
        }
        viewModelScope.launch {
            runCatching { billRepo.list(date = date, createdBy = staff, limit = PAGE, offset = 0) }
                .onSuccess { p -> _ui.update { it.copy(billsLoading = false, bills = p.items, hasMore = p.hasMore) } }
                .onFailure { e -> _ui.update { it.copy(billsLoading = false, error = friendlyError(e)) } }
        }
        refreshLeaderboard()
    }

    /** Bill-number search: type digits to find a bill across all history; blank clears. */
    fun setSearchNo(v: String) {
        val digits = v.filter { it.isDigit() }.take(9)
        _ui.update { it.copy(searchNo = digits) }
        if (digits.isBlank()) load() else runSearch()
    }

    private fun runSearch() {
        val query = _ui.value.searchNo
        val no = query.toIntOrNull()
        _ui.update { it.copy(billsLoading = true, hasMore = false, error = null) }
        viewModelScope.launch {
            runCatching { billRepo.list(date = null, createdBy = null, billNo = no, limit = PAGE, offset = 0) }
                // Ignore a stale response if the query moved on while we were fetching.
                .onSuccess { p -> if (_ui.value.searchNo == query) _ui.update { it.copy(billsLoading = false, bills = p.items, hasMore = false) } }
                .onFailure { e -> if (_ui.value.searchNo == query) _ui.update { it.copy(billsLoading = false, error = friendlyError(e)) } }
        }
    }

    fun loadMore() {
        val state = _ui.value
        if (state.isSearching || state.loadingMore || !state.hasMore) return
        _ui.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching { billRepo.list(date = state.date.toApiDate(), createdBy = state.selectedStaffId, limit = PAGE, offset = state.bills.size) }
                .onSuccess { p -> _ui.update { it.copy(loadingMore = false, bills = it.bills + p.items, hasMore = p.hasMore) } }
                .onFailure { e -> _ui.update { it.copy(loadingMore = false, message = UiText.err(e, R.string.err_generic)) } }
        }
    }

    fun changeDate(date: LocalDate) { _ui.update { it.copy(date = date) }; load() }
    fun goToPreviousDay() = changeDate(_ui.value.date.minusDays(1))
    fun goToNextDay() {
        val next = _ui.value.date.plusDays(1)
        if (!next.isAfter(todayInShopZone())) changeDate(next)
    }

    fun selectStaff(id: String?) { _ui.update { it.copy(selectedStaffId = id) }; load() }

    // ── Expense editor ──
    fun openCreateExpense() {
        _ui.update { it.copy(expenseEditor = ExpenseEditor()) }
        loadExpenseCategories()
    }
    fun openEditExpense(id: String, amount: Money, categoryId: String?, note: String?, paymentMethod: String) {
        _ui.update {
            it.copy(expenseEditor = ExpenseEditor(id = id, amount = amount.toWire(), categoryId = categoryId, note = note.orEmpty(), paymentMethod = paymentMethod))
        }
        loadExpenseCategories()
    }
    fun closeExpenseEditor() = _ui.update { it.copy(expenseEditor = null) }
    fun setExpenseAmount(v: String) = _ui.update { it.copy(expenseEditor = it.expenseEditor?.copy(amount = v, error = null)) }
    fun setExpenseCategory(id: String?) = _ui.update { it.copy(expenseEditor = it.expenseEditor?.copy(categoryId = id, error = null)) }
    fun setExpenseNote(v: String) = _ui.update { it.copy(expenseEditor = it.expenseEditor?.copy(note = v, error = null)) }
    fun setExpenseMethod(v: String) = _ui.update { it.copy(expenseEditor = it.expenseEditor?.copy(paymentMethod = v, error = null)) }

    private fun loadExpenseCategories() {
        viewModelScope.launch {
            runCatching { expenseRepo.listCategories() }
                .onSuccess { cats -> _ui.update { it.copy(expenseCategories = cats) } }
        }
    }

    /** Manager-only: create a category and select it in the open editor. */
    fun addExpenseCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { expenseRepo.createCategory(name) }
                .onSuccess { created ->
                    _ui.update {
                        it.copy(
                            expenseCategories = (it.expenseCategories + created).sortedBy { c -> c.name.lowercase() },
                            expenseEditor = it.expenseEditor?.copy(categoryId = created.id, error = null),
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(expenseEditor = it.expenseEditor?.copy(error = friendlyError(e))) } }
        }
    }

    fun saveExpense() {
        val editor = _ui.value.expenseEditor ?: return
        if (!editor.canSave) return
        val categoryId = editor.categoryId ?: return
        _ui.update { it.copy(expenseEditor = editor.copy(saving = true, error = null)) }
        viewModelScope.launch {
            val amount = Money.parse(editor.amount)
            val note = editor.note.trim().ifBlank { null }
            val result = runCatching {
                if (editor.id != null) expenseRepo.update(editor.id, amount, categoryId, note, editor.paymentMethod)
                else expenseRepo.add(amount, categoryId, note, editor.paymentMethod)
            }
            result
                .onSuccess { _ui.update { it.copy(expenseEditor = null) }; load() }
                .onFailure { e -> _ui.update { it.copy(expenseEditor = editor.copy(saving = false, error = friendlyError(e))) } }
        }
    }

    fun deleteExpense(id: String) {
        viewModelScope.launch {
            runCatching { expenseRepo.delete(id) }.onSuccess { load() }
                .onFailure { e -> _ui.update { it.copy(message = UiText.err(e, R.string.err_generic)) } }
        }
    }

    fun deleteBill(id: String) {
        viewModelScope.launch {
            runCatching { billRepo.delete(id) }
                .onSuccess { _ui.update { it.copy(message = UiText.res(R.string.vm_bill_deleted)) }; load() }
                .onFailure { e -> _ui.update { it.copy(message = UiText.err(e, R.string.err_generic)) } }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
