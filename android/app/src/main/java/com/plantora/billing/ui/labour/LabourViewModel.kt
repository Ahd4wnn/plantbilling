package com.plantora.billing.ui.labour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.R
import com.plantora.billing.data.LabourRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.Attendance
import com.plantora.billing.i18n.UiText
import com.plantora.billing.domain.Labourer
import com.plantora.billing.domain.LabourPayment
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.isMonthly
import com.plantora.billing.domain.parseApiDate
import com.plantora.billing.domain.todayInShopZone
import com.plantora.billing.domain.toApiDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

private fun daysDecimal(s: String): BigDecimal = s.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO

/** Amount = wage per day × number of days. */
private fun wageFor(wagePerDay: Money, days: String): Money =
    Money(wagePerDay.amount.multiply(daysDecimal(days)))

/**
 * What to pre-fill as a wage payment's amount.
 *
 * A daily worker is paid days × wage. A monthly worker isn't measured in days at
 * all, so "days × wage per day" would be a meaningless number — what's actually
 * being handed over is what's currently owed.
 */
private fun prefillWage(worker: Labourer, days: String): Money =
    if (worker.isMonthly) worker.balanceToPay.let { if (it.isNegative()) Money.ZERO else it }
    else wageFor(worker.defaultWage, days)

enum class LabourPayMode { CASH, UPI, SPLIT }

data class WorkerEditor(
    val id: String? = null,
    val name: String = "",
    val phone: String = "",
    val aadhaar: String = "",
    val gender: String = "male",
    /** "daily" or "monthly" — decides which wage field below is asked for. */
    val wageType: String = "daily",
    val wage: String = "",          // per day
    val monthlyWage: String = "",   // per month
    val paidLeaves: String = "",    // per month, monthly workers only
    // The day this worker joined. A new worker defaults to today; editing loads
    // whatever the shop recorded, so a late entry can be corrected.
    val joinedOn: LocalDate = todayInShopZone(),
    val saving: Boolean = false,
    val error: String? = null,
) {
    /** Mirrors Labourer.isMonthly, for the editor's own in-progress selection. */
    val isMonthly: Boolean get() = wageType == "monthly"

    /** The wage that matters for the chosen mode has to be filled in. Caught here
     *  so the message lands beside the empty field instead of coming back as a
     *  server error after the manager has already tapped Save. */
    val wageFilled: Boolean
        get() = (if (isMonthly) Money.parse(monthlyWage) else Money.parse(wage)).amount.signum() > 0

    val canSave: Boolean get() = name.isNotBlank() && wageFilled && !saving
}

data class PaymentEditor(
    val id: String? = null,
    val labourerId: String? = null,
    val labourerName: String = "",
    val isAdvance: Boolean = false,
    val wagePerDay: Money = Money.ZERO,
    /** Monthly workers get no "number of days" field — a salary isn't paid in days. */
    val isMonthlyWorker: Boolean = false,
    val monthlyWage: Money = Money.ZERO,
    val balanceToPay: Money = Money.ZERO,
    val days: String = "1",
    val amount: String = "",
    val mode: LabourPayMode = LabourPayMode.CASH,
    val splitCash: String = "",
    val note: String = "",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val total: Money get() = Money.parse(amount)

    val cash: Money
        get() = when (mode) {
            LabourPayMode.CASH -> total
            LabourPayMode.UPI -> Money.ZERO
            LabourPayMode.SPLIT -> Money.parse(splitCash)
        }
    val upi: Money
        get() = when (mode) {
            LabourPayMode.UPI -> total
            LabourPayMode.CASH -> Money.ZERO
            LabourPayMode.SPLIT -> (total - Money.parse(splitCash)).let { if (it.isNegative()) Money.ZERO else it }
        }

    val canSave: Boolean
        get() = labourerId != null && amount.isNotBlank() && !saving &&
            (mode != LabourPayMode.SPLIT || Money.parse(splitCash) <= total)
}

/** The worker whose statement/history sheet is open. */
data class WorkerDetail(
    val labourer: Labourer,
    val loading: Boolean = true,
    val payments: List<LabourPayment> = emptyList(),
)

data class LabourUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val isManager: Boolean = false,
    val labourers: List<Labourer> = emptyList(),
    val payments: List<LabourPayment> = emptyList(),
    val query: String = "",
    val workerEditor: WorkerEditor? = null,
    val paymentEditor: PaymentEditor? = null,
    val detail: WorkerDetail? = null,
    // Attendance (today).
    val showAttendance: Boolean = false,
    val attendance: Map<String, Attendance> = emptyMap(),  // labourerId -> record
    val attendanceBusyId: String? = null,
    val message: UiText? = null,
) {
    val filteredLabourers: List<Labourer>
        get() {
            val q = query.trim()
            if (q.isBlank()) return labourers
            return labourers.filter {
                it.name.contains(q, ignoreCase = true) || (it.phone?.contains(q) == true)
            }
        }
}

@HiltViewModel
class LabourViewModel @Inject constructor(
    private val repo: LabourRepository,
    session: com.plantora.billing.data.SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LabourUiState())
    val ui: StateFlow<LabourUiState> = _ui.asStateFlow()

    private val today: String get() = todayInShopZone().toApiDate()

    init {
        val isManager = (session.state.value as? com.plantora.billing.data.AuthState.Authenticated)?.user?.role ==
            com.plantora.billing.domain.Role.MANAGER
        _ui.update { it.copy(isManager = isManager) }
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val workers = repo.labourers()
                val pays = repo.payments()
                val att = repo.attendance(today).associateBy { it.labourerId }
                Triple(workers, pays, att)
            }.onSuccess { (workers, pays, att) ->
                _ui.update { it.copy(loading = false, labourers = workers, payments = pays, attendance = att) }
            }.onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }

    // ── Worker editor ──
    fun openAddWorker() = _ui.update { it.copy(workerEditor = WorkerEditor()) }
    fun openEditWorker(l: Labourer) = _ui.update {
        it.copy(
            workerEditor = WorkerEditor(
                id = l.id, name = l.name, phone = l.phone ?: "", aadhaar = l.aadhaar ?: "",
                gender = l.gender, wageType = l.wageType,
                wage = l.defaultWage.toInput(),
                monthlyWage = if (l.monthlyWage.amount.signum() > 0) l.monthlyWage.toInput() else "",
                paidLeaves = l.paidLeavesPerMonth.toString(),
                joinedOn = parseApiDate(l.joinedOn) ?: todayInShopZone(),
            ),
        )
    }
    fun closeWorker() = _ui.update { it.copy(workerEditor = null) }
    fun setWorkerName(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(name = v, error = null)) }
    fun setWorkerPhone(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(phone = v, error = null)) }
    fun setWorkerAadhaar(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(aadhaar = v, error = null)) }
    fun setWorkerGender(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(gender = v)) }
    fun setWorkerWage(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(wage = v, error = null)) }
    fun setWorkerWageType(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(wageType = v, error = null)) }
    fun setWorkerMonthlyWage(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(monthlyWage = v, error = null)) }
    fun setWorkerPaidLeaves(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(paidLeaves = v, error = null)) }
    fun setWorkerJoinedOn(v: LocalDate) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(joinedOn = v, error = null)) }

    fun saveWorker() {
        val e = _ui.value.workerEditor ?: return
        if (!e.canSave) return
        _ui.update { it.copy(workerEditor = e.copy(saving = true, error = null)) }
        viewModelScope.launch {
            runCatching {
                val joined = e.joinedOn.toApiDate()
                // Zero out the wage of the mode this worker is NOT paid in, so an
                // old figure can't linger and confuse a later reading of the row.
                val daily = if (e.isMonthly) Money.ZERO else Money.parse(e.wage)
                val monthly = if (e.isMonthly) Money.parse(e.monthlyWage) else Money.ZERO
                val leaves = if (e.isMonthly) e.paidLeaves.trim().toIntOrNull() ?: 0 else 0
                if (e.id == null) {
                    repo.addLabourer(e.name, e.phone, e.aadhaar, e.gender, e.wageType, daily, monthly, leaves, joined)
                } else {
                    repo.updateLabourer(e.id, e.name, e.phone, e.aadhaar, e.gender, e.wageType, daily, monthly, leaves, joined)
                }
            }.onSuccess { _ui.update { it.copy(workerEditor = null, message = UiText.res(R.string.vm_saved)) }; load() }
                .onFailure { err -> _ui.update { it.copy(workerEditor = e.copy(saving = false, error = friendlyError(err))) } }
        }
    }

    fun deleteWorker(id: String) {
        viewModelScope.launch {
            runCatching { repo.deleteLabourer(id) }
                .onSuccess { _ui.update { it.copy(message = UiText.res(R.string.vm_worker_removed), detail = null) }; load() }
                .onFailure { e -> _ui.update { it.copy(message = UiText.err(e, R.string.err_generic)) } }
        }
    }

    // ── Payment editor ──
    fun openRecordPayment(worker: Labourer? = null, advance: Boolean = false) {
        if (_ui.value.labourers.isEmpty()) { _ui.update { it.copy(message = UiText.res(R.string.vm_add_worker_first)) }; return }
        val editor = PaymentEditor(
            labourerId = worker?.id,
            labourerName = worker?.name ?: "",
            isAdvance = advance,
            wagePerDay = worker?.defaultWage ?: Money.ZERO,
            isMonthlyWorker = worker?.isMonthly == true,
            monthlyWage = worker?.monthlyWage ?: Money.ZERO,
            balanceToPay = worker?.balanceToPay ?: Money.ZERO,
            days = "1",
            amount = if (worker != null && !advance) prefillWage(worker, "1").toInput() else "",
        )
        _ui.update { it.copy(paymentEditor = editor) }
    }
    fun openEditPayment(p: LabourPayment) = _ui.update {
        val mode = when (p.paymentMethod) {
            com.plantora.billing.domain.PaymentMethod.UPI -> LabourPayMode.UPI
            com.plantora.billing.domain.PaymentMethod.SPLIT -> LabourPayMode.SPLIT
            else -> LabourPayMode.CASH
        }
        // Null when the worker has since been removed; the payment row survives.
        val worker = it.labourers.find { l -> l.id == p.labourerId }
        it.copy(paymentEditor = PaymentEditor(
            id = p.id, labourerId = p.labourerId ?: p.id, labourerName = p.labourerName,
            isAdvance = p.kind == "advance",
            wagePerDay = worker?.defaultWage ?: Money.ZERO,
            isMonthlyWorker = worker?.isMonthly == true,
            monthlyWage = worker?.monthlyWage ?: Money.ZERO,
            balanceToPay = worker?.balanceToPay ?: Money.ZERO,
            days = p.days ?: "1",
            amount = p.wageAmount.toInput(), mode = mode, splitCash = p.cashAmount.toInput(), note = p.note ?: "",
        ))
    }
    fun closePayment() = _ui.update { it.copy(paymentEditor = null) }
    fun selectPaymentLabourer(l: Labourer) = _ui.update {
        val ed = it.paymentEditor ?: return@update it
        val amount = if (!ed.isAdvance) prefillWage(l, ed.days).toInput() else ed.amount
        it.copy(paymentEditor = ed.copy(
            labourerId = l.id, labourerName = l.name,
            wagePerDay = l.defaultWage, isMonthlyWorker = l.isMonthly,
            monthlyWage = l.monthlyWage, balanceToPay = l.balanceToPay,
            amount = amount, error = null,
        ))
    }
    fun setPaymentDays(v: String) = _ui.update {
        val ed = it.paymentEditor ?: return@update it
        val amount = if (!ed.isAdvance) wageFor(ed.wagePerDay, v).toInput() else ed.amount
        it.copy(paymentEditor = ed.copy(days = v, amount = amount, error = null))
    }
    fun setPaymentAmount(v: String) = _ui.update { it.copy(paymentEditor = it.paymentEditor?.copy(amount = v, error = null)) }
    fun setPaymentAdvance(advance: Boolean) = _ui.update {
        val ed = it.paymentEditor ?: return@update it
        val amount = when {
            advance -> ""
            ed.isMonthlyWorker -> if (ed.balanceToPay.isNegative()) "" else ed.balanceToPay.toInput()
            else -> wageFor(ed.wagePerDay, ed.days).toInput()
        }
        it.copy(paymentEditor = ed.copy(isAdvance = advance, amount = amount, error = null))
    }
    fun setPaymentMode(m: LabourPayMode) = _ui.update {
        val ed = it.paymentEditor ?: return@update it
        val seeded = if (m == LabourPayMode.SPLIT && ed.splitCash.isBlank()) ed.copy(mode = m, splitCash = ed.total.toInput(), error = null) else ed.copy(mode = m, error = null)
        it.copy(paymentEditor = seeded)
    }
    fun setPaymentSplitCash(v: String) = _ui.update { it.copy(paymentEditor = it.paymentEditor?.copy(splitCash = v, error = null)) }
    fun setPaymentNote(v: String) = _ui.update { it.copy(paymentEditor = it.paymentEditor?.copy(note = v)) }

    fun savePayment() {
        val e = _ui.value.paymentEditor ?: return
        if (!e.canSave) return
        _ui.update { it.copy(paymentEditor = e.copy(saving = true, error = null)) }
        val amount = Money.parse(e.amount)
        // "Days covered" only means something for a daily wage. A salary payment
        // isn't measured in days, and the field is nullable for exactly this case.
        val days = if (e.isAdvance || e.isMonthlyWorker) null else e.days
        val kind = if (e.isAdvance) "advance" else "wage"
        viewModelScope.launch {
            runCatching {
                if (e.id == null) repo.recordPayment(e.labourerId!!, kind, amount, days, e.cash, e.upi, e.note)
                else repo.updatePayment(e.id, amount, days, e.cash, e.upi, e.note)
            }.onSuccess { _ui.update { it.copy(paymentEditor = null, message = UiText.res(R.string.vm_payment_recorded)) }; load(); refreshDetail() }
                .onFailure { err -> _ui.update { it.copy(paymentEditor = e.copy(saving = false, error = friendlyError(err))) } }
        }
    }

    fun deletePayment(id: String) {
        viewModelScope.launch {
            runCatching { repo.deletePayment(id) }
                .onSuccess { _ui.update { it.copy(message = UiText.res(R.string.vm_payment_deleted)) }; load(); refreshDetail() }
                .onFailure { e -> _ui.update { it.copy(message = UiText.err(e, R.string.err_generic)) } }
        }
    }

    // ── Worker detail (statement + history) ──
    fun openDetail(l: Labourer) {
        _ui.update { it.copy(detail = WorkerDetail(labourer = l, loading = true)) }
        viewModelScope.launch {
            runCatching { repo.payments(labourerId = l.id) }
                .onSuccess { list -> _ui.update { it.copy(detail = it.detail?.copy(loading = false, payments = list)) } }
                .onFailure { e -> _ui.update { it.copy(detail = it.detail?.copy(loading = false), message = UiText.err(e, R.string.err_generic)) } }
        }
    }
    fun closeDetail() = _ui.update { it.copy(detail = null) }
    private fun refreshDetail() {
        val d = _ui.value.detail ?: return
        // Re-point at the refreshed labourer row so the statement reflects new totals.
        val fresh = _ui.value.labourers.find { it.id == d.labourer.id } ?: d.labourer
        openDetail(fresh)
    }

    // ── Attendance ──
    fun openAttendance() = _ui.update { it.copy(showAttendance = true) }
    fun closeAttendance() = _ui.update { it.copy(showAttendance = false) }
    fun mark(labourerId: String, statusValue: String) {
        if (_ui.value.attendanceBusyId != null) return
        _ui.update { it.copy(attendanceBusyId = labourerId) }
        viewModelScope.launch {
            runCatching { repo.markAttendance(labourerId, today, statusValue) }
                .onSuccess { rec ->
                    _ui.update { s -> s.copy(attendanceBusyId = null, attendance = s.attendance + (labourerId to rec)) }
                    // Attendance changes days worked → refresh balances.
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(attendanceBusyId = null, message = UiText.err(e, R.string.err_generic)) } }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
