package com.plantora.billing.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.AuthState
import com.plantora.billing.data.BillRepository
import com.plantora.billing.data.SessionRepository
import com.plantora.billing.data.SettlementRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.BillListEntry
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Role
import com.plantora.billing.domain.daysSince
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Outstanding dues across the whole shop (shared by all staff). Collecting a due
 * settles it into cash and/or UPI. A manager's collection applies immediately; a
 * salesperson's collection is sent to a manager to approve first (the bill stays
 * outstanding, flagged "Waiting for approval", until then).
 */
/** Dues unpaid for at least this many days are flagged "priority" (overdue). */
private const val PRIORITY_DAYS = 30

/** How a collection is being split. */
enum class SettleMode { CASH, UPI, SPLIT }

/** The "collect this due" bottom sheet. */
data class SettleTarget(
    val entry: BillListEntry,
    val mode: SettleMode = SettleMode.CASH,
    val cash: String = "",
    val upi: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
) {
    /** The cash portion actually being collected, given the mode. */
    val cashAmount: Money
        get() = when (mode) {
            SettleMode.CASH -> entry.dueAmount
            SettleMode.UPI -> Money.ZERO
            SettleMode.SPLIT -> Money.parse(cash)
        }

    /** The UPI portion actually being collected, given the mode. */
    val upiAmount: Money
        get() = when (mode) {
            SettleMode.CASH -> Money.ZERO
            SettleMode.UPI -> entry.dueAmount
            SettleMode.SPLIT -> Money.parse(upi)
        }

    /** Split must add up exactly to what's owed. */
    val balances: Boolean
        get() = (cashAmount.amount + upiAmount.amount).compareTo(entry.dueAmount.amount) == 0
}

data class DuesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val dues: List<BillListEntry> = emptyList(),
    val query: String = "",
    val isManager: Boolean = false,
    val settle: SettleTarget? = null,
    val message: String? = null,
) {
    val totalOwed: Money get() = dues.fold(Money.ZERO) { acc, b -> acc + b.dueAmount }

    /** Dues matching the search box (by customer name or phone). */
    private val filtered: List<BillListEntry>
        get() {
            val q = query.trim()
            if (q.isBlank()) return dues
            return dues.filter { e ->
                e.customerName?.contains(q, ignoreCase = true) == true ||
                    e.customerPhone?.contains(q) == true
            }
        }

    /** Overdue (30+ days) dues, oldest first — shown in the priority section. */
    val priorityDues: List<BillListEntry>
        get() = filtered.filter { (daysSince(it.createdAt) ?: 0) >= PRIORITY_DAYS }
            .sortedByDescending { daysSince(it.createdAt) ?: 0 }

    /** The remaining (newer) dues. */
    val otherDues: List<BillListEntry>
        get() = filtered.filter { (daysSince(it.createdAt) ?: 0) < PRIORITY_DAYS }

    val hasResults: Boolean get() = filtered.isNotEmpty()
}

@HiltViewModel
class DuesViewModel @Inject constructor(
    private val billRepo: BillRepository,
    private val settlementRepo: SettlementRepository,
    session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(DuesUiState())
    val ui: StateFlow<DuesUiState> = _ui.asStateFlow()

    init {
        val isManager = (session.state.value as? AuthState.Authenticated)?.user?.role == Role.MANAGER
        _ui.update { it.copy(isManager = isManager) }
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { billRepo.listDues() }
                .onSuccess { page -> _ui.update { it.copy(loading = false, dues = page.items) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    // ── Collect (settle) sheet ──
    fun openSettle(entry: BillListEntry) = _ui.update { it.copy(settle = SettleTarget(entry)) }
    fun closeSettle() = _ui.update { it.copy(settle = null) }
    fun setSettleMode(mode: SettleMode) = _ui.update { s ->
        // Seed the split fields with the full amount in cash so it starts balanced.
        val t = s.settle ?: return@update s
        val seeded = if (mode == SettleMode.SPLIT && t.cash.isBlank() && t.upi.isBlank())
            t.copy(mode = mode, cash = t.entry.dueAmount.toWire(), upi = "0", error = null)
        else t.copy(mode = mode, error = null)
        s.copy(settle = seeded)
    }

    fun setSettleCash(v: String) = _ui.update { s ->
        val t = s.settle ?: return@update s
        // In split mode, auto-fill UPI with the remainder so the two always balance.
        val remainder = t.entry.dueAmount.amount - (Money.parse(v).amount)
        val upi = if (remainder.signum() >= 0) Money(remainder).toWire() else "0"
        s.copy(settle = t.copy(cash = v, upi = upi, error = null))
    }

    fun setSettleUpi(v: String) = _ui.update { s ->
        val t = s.settle ?: return@update s
        val remainder = t.entry.dueAmount.amount - (Money.parse(v).amount)
        val cash = if (remainder.signum() >= 0) Money(remainder).toWire() else "0"
        s.copy(settle = t.copy(upi = v, cash = cash, error = null))
    }

    fun confirmSettle() {
        val t = _ui.value.settle ?: return
        if (t.submitting) return
        if (!t.balances) {
            _ui.update { it.copy(settle = t.copy(error = "Cash + UPI must equal ${t.entry.dueAmount.format()}.")) }
            return
        }
        _ui.update { it.copy(settle = t.copy(submitting = true, error = null)) }
        viewModelScope.launch {
            runCatching { settlementRepo.collect(t.entry.id, t.cashAmount, t.upiAmount) }
                .onSuccess { status ->
                    if (status == "approved") {
                        // Applied now — drop it from the outstanding list.
                        _ui.update { s ->
                            s.copy(
                                settle = null,
                                dues = s.dues.filterNot { it.id == t.entry.id },
                                message = "Collected — ${t.entry.dueAmount.format()}.",
                            )
                        }
                    } else {
                        // Pending — keep it in the list but flag it so it can't be
                        // collected again while a manager reviews it.
                        _ui.update { s ->
                            s.copy(
                                settle = null,
                                dues = s.dues.map { if (it.id == t.entry.id) it.copy(pendingSettlement = true) else it },
                                message = "Sent to the manager for approval.",
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(settle = it.settle?.copy(submitting = false, error = friendlyError(e, "Couldn't record the collection."))) }
                }
        }
    }

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }

    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
