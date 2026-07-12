package com.plantora.billing.ui.labour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.LabourRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.Attendance
import com.plantora.billing.domain.Labourer
import com.plantora.billing.domain.LabourPayment
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.todayInShopZone
import com.plantora.billing.domain.toApiDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

private fun hoursToDecimal(s: String): BigDecimal = s.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO

enum class LabourPayMode { CASH, UPI, SPLIT, DUE }

data class WorkerEditor(
    val id: String? = null,
    val name: String = "",
    val phone: String = "",
    val gender: String = "male",
    val wage: String = "",
    val otRate: String = "",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

data class PaymentEditor(
    val id: String? = null,
    val labourerId: String? = null,
    val labourerName: String = "",
    val wage: String = "",
    val otHours: String = "",
    val otRate: Money = Money.ZERO,
    val mode: LabourPayMode = LabourPayMode.CASH,
    val splitCash: String = "",
    val note: String = "",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val overtimeAmount: Money get() = Money(otRate.amount.multiply(hoursToDecimal(otHours)))
    val total: Money get() = Money.parse(wage) + overtimeAmount

    val cash: Money
        get() = when (mode) {
            LabourPayMode.CASH -> total
            LabourPayMode.UPI, LabourPayMode.DUE -> Money.ZERO
            LabourPayMode.SPLIT -> Money.parse(splitCash)
        }
    val upi: Money
        get() = when (mode) {
            LabourPayMode.UPI -> total
            LabourPayMode.CASH, LabourPayMode.DUE -> Money.ZERO
            LabourPayMode.SPLIT -> (total - Money.parse(splitCash)).let { if (it.isNegative()) Money.ZERO else it }
        }
    val due: Money get() = if (mode == LabourPayMode.DUE) total else Money.ZERO

    val canSave: Boolean
        get() = labourerId != null && wage.isNotBlank() && !saving &&
            (mode != LabourPayMode.SPLIT || Money.parse(splitCash) <= total)
}

/** Pay off a worker's outstanding due. */
data class ClearDueEditor(
    val labourerId: String,
    val labourerName: String,
    val outstanding: Money,
    val amount: String = "",
    val viaUpi: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

/** The worker whose history/detail sheet is open. */
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
    val clearDue: ClearDueEditor? = null,
    val detail: WorkerDetail? = null,
    // Attendance (today).
    val showAttendance: Boolean = false,
    val attendance: Map<String, Attendance> = emptyMap(),  // labourerId -> record
    val attendanceBusyId: String? = null,
    val message: String? = null,
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
        it.copy(workerEditor = WorkerEditor(id = l.id, name = l.name, phone = l.phone ?: "", gender = l.gender, wage = l.defaultWage.toInput(), otRate = l.overtimeRate.toInput()))
    }
    fun closeWorker() = _ui.update { it.copy(workerEditor = null) }
    fun setWorkerName(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(name = v, error = null)) }
    fun setWorkerPhone(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(phone = v, error = null)) }
    fun setWorkerGender(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(gender = v)) }
    fun setWorkerWage(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(wage = v, error = null)) }
    fun setWorkerOtRate(v: String) = _ui.update { it.copy(workerEditor = it.workerEditor?.copy(otRate = v, error = null)) }

    fun saveWorker() {
        val e = _ui.value.workerEditor ?: return
        if (!e.canSave) return
        _ui.update { it.copy(workerEditor = e.copy(saving = true, error = null)) }
        viewModelScope.launch {
            runCatching {
                if (e.id == null) repo.addLabourer(e.name, e.phone, e.gender, Money.parse(e.wage), Money.parse(e.otRate))
                else repo.updateLabourer(e.id, e.name, e.phone, e.gender, Money.parse(e.wage), Money.parse(e.otRate))
            }.onSuccess { _ui.update { it.copy(workerEditor = null, message = "Saved.") }; load() }
                .onFailure { err -> _ui.update { it.copy(workerEditor = e.copy(saving = false, error = friendlyError(err))) } }
        }
    }

    fun deleteWorker(id: String) {
        viewModelScope.launch {
            runCatching { repo.deleteLabourer(id) }
                .onSuccess { _ui.update { it.copy(message = "Worker removed.", detail = null) }; load() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }

    // ── Payment editor ──
    fun openRecordPayment() {
        if (_ui.value.labourers.isEmpty()) { _ui.update { it.copy(message = "Add a worker first.") }; return }
        _ui.update { it.copy(paymentEditor = PaymentEditor()) }
    }
    fun openEditPayment(p: LabourPayment) = _ui.update {
        val mode = when (p.paymentMethod) {
            com.plantora.billing.domain.PaymentMethod.UPI -> LabourPayMode.UPI
            com.plantora.billing.domain.PaymentMethod.DUE -> LabourPayMode.DUE
            com.plantora.billing.domain.PaymentMethod.SPLIT -> LabourPayMode.SPLIT
            else -> LabourPayMode.CASH
        }
        it.copy(paymentEditor = PaymentEditor(id = p.id, labourerId = p.labourerId ?: p.id, labourerName = p.labourerName, wage = p.wageAmount.toInput(), otHours = p.overtimeHours, otRate = p.overtimeRate, mode = mode, splitCash = p.cashAmount.toInput(), note = p.note ?: ""))
    }
    fun closePayment() = _ui.update { it.copy(paymentEditor = null) }
    fun selectPaymentLabourer(l: Labourer) = _ui.update {
        it.copy(paymentEditor = it.paymentEditor?.copy(labourerId = l.id, labourerName = l.name, wage = l.defaultWage.toInput(), otRate = l.overtimeRate, error = null))
    }
    fun setPaymentWage(v: String) = _ui.update { it.copy(paymentEditor = it.paymentEditor?.copy(wage = v, error = null)) }
    fun setPaymentHours(v: String) = _ui.update { it.copy(paymentEditor = it.paymentEditor?.copy(otHours = v, error = null)) }
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
        viewModelScope.launch {
            runCatching {
                if (e.id == null) repo.recordPayment(e.labourerId!!, Money.parse(e.wage), e.otHours, e.cash, e.upi, e.due, e.note)
                else repo.updatePayment(e.id, Money.parse(e.wage), e.otHours, e.cash, e.upi, e.due, e.note)
            }.onSuccess { _ui.update { it.copy(paymentEditor = null, message = "Payment recorded.") }; load(); refreshDetail() }
                .onFailure { err -> _ui.update { it.copy(paymentEditor = e.copy(saving = false, error = friendlyError(err))) } }
        }
    }

    fun deletePayment(id: String) {
        viewModelScope.launch {
            runCatching { repo.deletePayment(id) }
                .onSuccess { _ui.update { it.copy(message = "Payment deleted.") }; load(); refreshDetail() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }

    // ── Worker detail (history) ──
    fun openDetail(l: Labourer) {
        _ui.update { it.copy(detail = WorkerDetail(labourer = l, loading = true)) }
        viewModelScope.launch {
            runCatching { repo.payments(labourerId = l.id) }
                .onSuccess { list -> _ui.update { it.copy(detail = it.detail?.copy(loading = false, payments = list)) } }
                .onFailure { e -> _ui.update { it.copy(detail = it.detail?.copy(loading = false), message = friendlyError(e)) } }
        }
    }
    fun closeDetail() = _ui.update { it.copy(detail = null) }
    private fun refreshDetail() {
        val d = _ui.value.detail ?: return
        openDetail(d.labourer)
    }

    // ── Clear due ──
    fun openClearDue(l: Labourer) = _ui.update {
        it.copy(clearDue = ClearDueEditor(labourerId = l.id, labourerName = l.name, outstanding = l.outstandingDue, amount = l.outstandingDue.toInput()))
    }
    fun closeClearDue() = _ui.update { it.copy(clearDue = null) }
    fun setClearDueAmount(v: String) = _ui.update { it.copy(clearDue = it.clearDue?.copy(amount = v, error = null)) }
    fun setClearDueViaUpi(v: Boolean) = _ui.update { it.copy(clearDue = it.clearDue?.copy(viaUpi = v)) }
    fun confirmClearDue() {
        val e = _ui.value.clearDue ?: return
        val amt = Money.parse(e.amount)
        if (!amt.isPositive()) { _ui.update { it.copy(clearDue = e.copy(error = "Enter an amount.")) }; return }
        _ui.update { it.copy(clearDue = e.copy(saving = true, error = null)) }
        viewModelScope.launch {
            runCatching {
                repo.clearDue(e.labourerId, if (e.viaUpi) Money.ZERO else amt, if (e.viaUpi) amt else Money.ZERO, "Cleared due")
            }.onSuccess { _ui.update { it.copy(clearDue = null, message = "Due cleared.") }; load(); refreshDetail() }
                .onFailure { err -> _ui.update { it.copy(clearDue = e.copy(saving = false, error = friendlyError(err))) } }
        }
    }

    // ── Attendance ──
    fun openAttendance() = _ui.update { it.copy(showAttendance = true) }
    fun closeAttendance() = _ui.update { it.copy(showAttendance = false) }
    fun mark(labourerId: String, statusValue: String, overtimeHours: String = "0") {
        if (_ui.value.attendanceBusyId != null) return
        _ui.update { it.copy(attendanceBusyId = labourerId) }
        viewModelScope.launch {
            runCatching { repo.markAttendance(labourerId, today, statusValue, overtimeHours) }
                .onSuccess { rec -> _ui.update { s -> s.copy(attendanceBusyId = null, attendance = s.attendance + (labourerId to rec)) } }
                .onFailure { e -> _ui.update { it.copy(attendanceBusyId = null, message = friendlyError(e)) } }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
