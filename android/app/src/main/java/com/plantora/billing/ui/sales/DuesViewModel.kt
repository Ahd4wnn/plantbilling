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

/** The "collect this due" bottom sheet. A partial amount may be collected — the
 *  rest stays owed. `amount` is how much to collect now (≤ the due). */
data class SettleTarget(
    val entry: BillListEntry,
    val amount: String = entry.dueAmount.toWire(),
    val mode: SettleMode = SettleMode.CASH,
    val splitCash: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
) {
    /** How much is being collected now. */
    val total: Money get() = Money.parse(amount)

    /** The cash portion being collected, given the mode. */
    val cashAmount: Money
        get() = when (mode) {
            SettleMode.CASH -> total
            SettleMode.UPI -> Money.ZERO
            SettleMode.SPLIT -> Money.parse(splitCash)
        }

    /** The UPI portion being collected, given the mode. */
    val upiAmount: Money
        get() = when (mode) {
            SettleMode.CASH -> Money.ZERO
            SettleMode.UPI -> total
            SettleMode.SPLIT -> (total - Money.parse(splitCash)).let { if (it.isNegative()) Money.ZERO else it }
        }

    /** Positive, no more than what's owed, and any split fits inside the amount. */
    val valid: Boolean
        get() = total.isPositive() && total <= entry.dueAmount &&
            (mode != SettleMode.SPLIT || Money.parse(splitCash) <= total)
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
    fun setSettleAmount(v: String) = _ui.update { s ->
        val t = s.settle ?: return@update s
        s.copy(settle = t.copy(amount = v, error = null))
    }

    fun setSettleMode(mode: SettleMode) = _ui.update { s ->
        // Seed the split's cash part with the whole amount so it starts valid.
        val t = s.settle ?: return@update s
        val seeded = if (mode == SettleMode.SPLIT && t.splitCash.isBlank())
            t.copy(mode = mode, splitCash = t.total.toWire(), error = null)
        else t.copy(mode = mode, error = null)
        s.copy(settle = seeded)
    }

    fun setSettleSplitCash(v: String) = _ui.update { s ->
        val t = s.settle ?: return@update s
        s.copy(settle = t.copy(splitCash = v, error = null))
    }

    fun confirmSettle() {
        val t = _ui.value.settle ?: return
        if (t.submitting) return
        if (!t.valid) {
            _ui.update { it.copy(settle = t.copy(error = "Enter an amount up to ${t.entry.dueAmount.format()}.")) }
            return
        }
        val remainingDue = (t.entry.dueAmount - t.total).let { if (it.isNegative()) Money.ZERO else it }
        _ui.update { it.copy(settle = t.copy(submitting = true, error = null)) }
        viewModelScope.launch {
            runCatching { settlementRepo.collect(t.entry.id, t.cashAmount, t.upiAmount) }
                .onSuccess { status ->
                    if (status == "approved") {
                        // Applied now. Drop it if fully cleared, else keep with the
                        // reduced due still showing.
                        _ui.update { s ->
                            s.copy(
                                settle = null,
                                dues = if (remainingDue.isPositive())
                                    s.dues.map { if (it.id == t.entry.id) it.copy(dueAmount = remainingDue) else it }
                                else s.dues.filterNot { it.id == t.entry.id },
                                message = if (remainingDue.isPositive())
                                    "Collected ${t.total.format()} — ${remainingDue.format()} still owed."
                                else "Collected — ${t.total.format()}.",
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
