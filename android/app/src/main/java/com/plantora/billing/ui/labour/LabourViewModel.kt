package com.plantora.billing.ui.labour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.AuthState
import com.plantora.billing.data.LabourRepository
import com.plantora.billing.data.SessionRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.Labourer
import com.plantora.billing.domain.LabourPayment
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

private fun hoursToDecimal(s: String): BigDecimal = s.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO

/** Add/edit a worker (manager only). */
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

/** Record (or edit) a payment. */
data class PaymentEditor(
    val id: String? = null,            // non-null when editing an existing payment
    val labourerId: String? = null,
    val labourerName: String = "",
    val wage: String = "",
    val otHours: String = "",
    val otRate: Money = Money.ZERO,
    val note: String = "",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val overtimeAmount: Money get() = Money(otRate.amount.multiply(hoursToDecimal(otHours)))
    val total: Money get() = Money.parse(wage) + overtimeAmount
    val canSave: Boolean get() = labourerId != null && wage.isNotBlank() && !saving
}

data class LabourUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val isManager: Boolean = false,
    val labourers: List<Labourer> = emptyList(),
    val payments: List<LabourPayment> = emptyList(),
    val workerEditor: WorkerEditor? = null,
    val paymentEditor: PaymentEditor? = null,
    val message: String? = null,
)

@HiltViewModel
class LabourViewModel @Inject constructor(
    private val repo: LabourRepository,
    session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LabourUiState())
    val ui: StateFlow<LabourUiState> = _ui.asStateFlow()

    init {
        val isManager = (session.state.value as? AuthState.Authenticated)?.user?.role == Role.MANAGER
        _ui.update { it.copy(isManager = isManager) }
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val workers = repo.labourers()
                val pays = repo.payments()
                workers to pays
            }.onSuccess { (workers, pays) ->
                _ui.update { it.copy(loading = false, labourers = workers, payments = pays) }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    // ── Worker editor (manager) ──
    fun openAddWorker() = _ui.update { it.copy(workerEditor = WorkerEditor()) }
    fun openEditWorker(l: Labourer) = _ui.update {
        it.copy(
            workerEditor = WorkerEditor(
                id = l.id,
                name = l.name,
                phone = l.phone ?: "",
                gender = l.gender,
                wage = l.defaultWage.toInput(),
                otRate = l.overtimeRate.toInput(),
            ),
        )
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
                if (e.id == null) {
                    repo.addLabourer(e.name, e.phone, e.gender, Money.parse(e.wage), Money.parse(e.otRate))
                } else {
                    repo.updateLabourer(e.id, e.name, e.phone, e.gender, Money.parse(e.wage), Money.parse(e.otRate))
                }
            }.onSuccess {
                _ui.update { it.copy(workerEditor = null, message = "Saved.") }
                load()
            }.onFailure { err ->
                _ui.update { it.copy(workerEditor = e.copy(saving = false, error = friendlyError(err))) }
            }
        }
    }

    fun deleteWorker(id: String) {
        viewModelScope.launch {
            runCatching { repo.deleteLabourer(id) }
                .onSuccess { _ui.update { it.copy(message = "Worker removed.") }; load() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }

    // ── Payment editor (both roles) ──
    fun openRecordPayment() {
        if (_ui.value.labourers.isEmpty()) {
            _ui.update { it.copy(message = "Add a worker first before recording a payment.") }
            return
        }
        _ui.update { it.copy(paymentEditor = PaymentEditor()) }
    }

    fun openEditPayment(p: LabourPayment) = _ui.update {
        it.copy(
            paymentEditor = PaymentEditor(
                id = p.id,
                labourerId = p.labourerId ?: p.id,  // any non-null so canSave passes; PATCH ignores it
                labourerName = p.labourerName,
                wage = p.wageAmount.toInput(),
                otHours = p.overtimeHours,
                otRate = p.overtimeRate,
                note = p.note ?: "",
            ),
        )
    }

    fun closePayment() = _ui.update { it.copy(paymentEditor = null) }

    fun selectPaymentLabourer(l: Labourer) = _ui.update {
        it.copy(
            paymentEditor = it.paymentEditor?.copy(
                labourerId = l.id,
                labourerName = l.name,
                wage = l.defaultWage.toInput(),
                otRate = l.overtimeRate,
                error = null,
            ),
        )
    }

    fun setPaymentWage(v: String) = _ui.update { it.copy(paymentEditor = it.paymentEditor?.copy(wage = v, error = null)) }
    fun setPaymentHours(v: String) = _ui.update { it.copy(paymentEditor = it.paymentEditor?.copy(otHours = v, error = null)) }
    fun setPaymentNote(v: String) = _ui.update { it.copy(paymentEditor = it.paymentEditor?.copy(note = v)) }

    fun savePayment() {
        val e = _ui.value.paymentEditor ?: return
        if (!e.canSave) return
        _ui.update { it.copy(paymentEditor = e.copy(saving = true, error = null)) }
        viewModelScope.launch {
            runCatching {
                if (e.id == null) {
                    repo.recordPayment(e.labourerId!!, Money.parse(e.wage), e.otHours, e.note)
                } else {
                    repo.updatePayment(e.id, Money.parse(e.wage), e.otHours, e.note)
                }
            }.onSuccess {
                _ui.update { it.copy(paymentEditor = null, message = "Payment recorded.") }
                load()
            }.onFailure { err ->
                _ui.update { it.copy(paymentEditor = e.copy(saving = false, error = friendlyError(err))) }
            }
        }
    }

    fun deletePayment(id: String) {
        viewModelScope.launch {
            runCatching { repo.deletePayment(id) }
                .onSuccess { _ui.update { it.copy(message = "Payment deleted.") }; load() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
