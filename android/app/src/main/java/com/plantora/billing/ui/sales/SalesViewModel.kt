package com.plantora.billing.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.AuthState
import com.plantora.billing.data.BillRepository
import com.plantora.billing.data.ExpenseRepository
import com.plantora.billing.data.SalespersonRepository
import com.plantora.billing.data.SessionRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.BillListEntry
import com.plantora.billing.domain.DaySummary
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
    val reason: String = "",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = Money.parse(amount).isPositive() && reason.isNotBlank() && !saving
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
    val error: String? = null,
    val bills: List<BillListEntry> = emptyList(),
    val billsLoading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val expenseEditor: ExpenseEditor? = null,
    val message: String? = null,
) {
    val isToday: Boolean get() = date == todayInShopZone()
    val selectedStaffLabel: String
        get() = staff.find { it.id == selectedStaffId }?.email ?: "All staff"
}

@HiltViewModel
class SalesViewModel @Inject constructor(
    private val billRepo: BillRepository,
    private val expenseRepo: ExpenseRepository,
    private val salespersonRepo: SalespersonRepository,
    session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SalesUiState())
    val ui: StateFlow<SalesUiState> = _ui.asStateFlow()

    init {
        val owner = (session.state.value as? AuthState.Authenticated)?.user?.role == Role.MANAGER
        _ui.update { it.copy(isOwner = owner) }
        if (owner) loadStaff()
        load()
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

    fun loadMore() {
        val state = _ui.value
        if (state.loadingMore || !state.hasMore) return
        _ui.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching { billRepo.list(date = state.date.toApiDate(), createdBy = state.selectedStaffId, limit = PAGE, offset = state.bills.size) }
                .onSuccess { p -> _ui.update { it.copy(loadingMore = false, bills = it.bills + p.items, hasMore = p.hasMore) } }
                .onFailure { e -> _ui.update { it.copy(loadingMore = false, message = friendlyError(e)) } }
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
    fun openCreateExpense() = _ui.update { it.copy(expenseEditor = ExpenseEditor()) }
    fun openEditExpense(id: String, amount: Money, reason: String) =
        _ui.update { it.copy(expenseEditor = ExpenseEditor(id = id, amount = amount.toWire(), reason = reason)) }
    fun closeExpenseEditor() = _ui.update { it.copy(expenseEditor = null) }
    fun setExpenseAmount(v: String) = _ui.update { it.copy(expenseEditor = it.expenseEditor?.copy(amount = v, error = null)) }
    fun setExpenseReason(v: String) = _ui.update { it.copy(expenseEditor = it.expenseEditor?.copy(reason = v, error = null)) }

    fun saveExpense() {
        val editor = _ui.value.expenseEditor ?: return
        if (!editor.canSave) return
        _ui.update { it.copy(expenseEditor = editor.copy(saving = true, error = null)) }
        viewModelScope.launch {
            val amount = Money.parse(editor.amount)
            val result = runCatching {
                if (editor.id != null) expenseRepo.update(editor.id, amount, editor.reason)
                else expenseRepo.add(amount, editor.reason)
            }
            result
                .onSuccess { _ui.update { it.copy(expenseEditor = null) }; load() }
                .onFailure { e -> _ui.update { it.copy(expenseEditor = editor.copy(saving = false, error = friendlyError(e))) } }
        }
    }

    fun deleteExpense(id: String) {
        viewModelScope.launch {
            runCatching { expenseRepo.delete(id) }.onSuccess { load() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }

    fun deleteBill(id: String) {
        viewModelScope.launch {
            runCatching { billRepo.delete(id) }
                .onSuccess { _ui.update { it.copy(message = "Bill deleted.") }; load() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
