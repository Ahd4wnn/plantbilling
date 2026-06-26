package com.plantora.billing.ui.settings.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.SalespersonRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.Salesperson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

/** Result of a create/reset action — shows the one-time credentials. */
data class CredentialResult(val email: String, val password: String, val isReset: Boolean)

data class CreateForm(
    val email: String = "",
    val password: String = generatePassword(),
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = email.contains("@") && password.length >= 8 && !saving
}

/** Reset a specific salesperson's password to a chosen (or generated) value. */
data class ResetForm(
    val sp: Salesperson,
    val password: String = generatePassword(),
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = password.length >= 8 && !saving
}

data class StaffUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val staff: List<Salesperson> = emptyList(),
    val createForm: CreateForm? = null,
    val resetForm: ResetForm? = null,
    val credentials: CredentialResult? = null,
    val message: String? = null,
)

@HiltViewModel
class StaffViewModel @Inject constructor(
    private val repo: SalespersonRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(StaffUiState())
    val ui: StateFlow<StaffUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.list() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, staff = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun openCreate() = _ui.update { it.copy(createForm = CreateForm()) }
    fun closeCreate() = _ui.update { it.copy(createForm = null) }
    fun setEmail(v: String) = _ui.update { it.copy(createForm = it.createForm?.copy(email = v, error = null)) }
    fun setPassword(v: String) = _ui.update { it.copy(createForm = it.createForm?.copy(password = v, error = null)) }
    fun regeneratePassword() = _ui.update { it.copy(createForm = it.createForm?.copy(password = generatePassword(), error = null)) }
    fun dismissCredentials() = _ui.update { it.copy(credentials = null) }
    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun createStaff() {
        val form = _ui.value.createForm ?: return
        if (!form.canSave) return
        _ui.update { it.copy(createForm = form.copy(saving = true, error = null)) }
        viewModelScope.launch {
            runCatching { repo.create(form.email, form.password) }
                .onSuccess {
                    _ui.update { it.copy(createForm = null, credentials = CredentialResult(form.email.trim(), form.password, isReset = false)) }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(createForm = form.copy(saving = false, error = friendlyError(e))) } }
        }
    }

    fun toggleActive(sp: Salesperson) {
        viewModelScope.launch {
            runCatching { repo.setActive(sp.id, !sp.isActive) }
                .onSuccess { _ui.update { it.copy(message = if (sp.isActive) "Deactivated ${sp.email}" else "Activated ${sp.email}") }; load() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }

    fun openReset(sp: Salesperson) = _ui.update { it.copy(resetForm = ResetForm(sp)) }
    fun closeReset() = _ui.update { it.copy(resetForm = null) }
    fun setResetPassword(v: String) = _ui.update { it.copy(resetForm = it.resetForm?.copy(password = v, error = null)) }
    fun regenerateResetPassword() = _ui.update { it.copy(resetForm = it.resetForm?.copy(password = generatePassword(), error = null)) }

    fun confirmReset() {
        val form = _ui.value.resetForm ?: return
        if (!form.canSave) return
        _ui.update { it.copy(resetForm = form.copy(saving = true, error = null)) }
        viewModelScope.launch {
            runCatching { repo.resetPassword(form.sp.id, form.password) }
                .onSuccess {
                    _ui.update { it.copy(resetForm = null, credentials = CredentialResult(form.sp.email, form.password, isReset = true)) }
                }
                .onFailure { e -> _ui.update { it.copy(resetForm = form.copy(saving = false, error = friendlyError(e))) } }
        }
    }

    fun deleteStaff(sp: Salesperson) {
        viewModelScope.launch {
            runCatching { repo.delete(sp.id) }
                .onSuccess { _ui.update { it.copy(message = "Removed ${sp.email}") }; load() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }
}

private fun generatePassword(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
    val rnd = SecureRandom()
    return (1..10).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
}
