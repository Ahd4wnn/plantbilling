package com.plantora.billing.ui.owner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.OwnerRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.DetailedReport
import com.plantora.billing.domain.OwnerShop
import com.plantora.billing.domain.OwnerStaff
import com.plantora.billing.domain.toApiDate
import com.plantora.billing.domain.todayInShopZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewStaffForm(
    val email: String = "",
    val password: String = "",
    val role: String = "salesperson",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = email.contains("@") && password.length >= 8 && !saving
}

data class OwnerShopState(
    val loading: Boolean = true,
    val error: String? = null,
    val shop: OwnerShop? = null,
    val report: DetailedReport? = null,
    val period: OwnerPeriod = OwnerPeriod.TODAY,
    val staff: List<OwnerStaff> = emptyList(),
    val newStaff: NewStaffForm = NewStaffForm(),
    val message: String? = null,
)

@HiltViewModel
class OwnerShopViewModel @Inject constructor(
    private val repo: OwnerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val shopId: String = checkNotNull(savedStateHandle["shopId"])

    private val _ui = MutableStateFlow(OwnerShopState())
    val ui: StateFlow<OwnerShopState> = _ui.asStateFlow()

    init { load(); loadReport(); loadStaff() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.shops().find { it.id == shopId } }
                .onSuccess { s -> _ui.update { it.copy(loading = false, shop = s) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun setPeriod(p: OwnerPeriod) {
        if (p == _ui.value.period) return
        _ui.update { it.copy(period = p) }
        loadReport()
    }

    private fun loadReport() {
        val today = todayInShopZone()
        val from = when (_ui.value.period) {
            OwnerPeriod.TODAY -> today
            OwnerPeriod.WEEK -> today.minusDays(6)
            OwnerPeriod.MONTH -> today.withDayOfMonth(1)
        }
        viewModelScope.launch {
            runCatching { repo.report(shopId, from.toApiDate(), today.toApiDate()) }
                .onSuccess { r -> _ui.update { it.copy(report = r) } }
        }
    }

    private fun loadStaff() {
        viewModelScope.launch {
            runCatching { repo.staff(shopId) }.onSuccess { s -> _ui.update { it.copy(staff = s) } }
        }
    }

    fun setStaffEmail(v: String) = _ui.update { it.copy(newStaff = it.newStaff.copy(email = v, error = null)) }
    fun setStaffPassword(v: String) = _ui.update { it.copy(newStaff = it.newStaff.copy(password = v, error = null)) }
    fun setStaffRole(v: String) = _ui.update { it.copy(newStaff = it.newStaff.copy(role = v)) }
    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun addStaff() {
        val form = _ui.value.newStaff
        if (!form.canSave) return
        _ui.update { it.copy(newStaff = form.copy(saving = true, error = null)) }
        viewModelScope.launch {
            runCatching { repo.createStaff(shopId, form.email, form.password, form.role) }
                .onSuccess {
                    _ui.update { it.copy(newStaff = NewStaffForm(), message = "Staff added.") }
                    loadStaff()
                }
                .onFailure { e -> _ui.update { it.copy(newStaff = form.copy(saving = false, error = friendlyError(e))) } }
        }
    }

    fun deleteStaff(s: OwnerStaff) {
        viewModelScope.launch {
            runCatching { repo.deleteStaff(shopId, s.id) }
                .onSuccess { _ui.update { it.copy(message = "Removed ${s.email}") }; loadStaff() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }
}
