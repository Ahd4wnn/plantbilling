package com.plantora.billing.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.SessionRepository
import com.plantora.billing.data.remote.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !submitting
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    fun onEmailChange(value: String) = _ui.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, error = null) }

    fun submit() {
        val state = _ui.value
        if (!state.canSubmit) return
        _ui.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            session.login(state.email, state.password)
                .onFailure { e ->
                    _ui.update {
                        it.copy(
                            submitting = false,
                            error = friendlyError(e, "Couldn't sign in. Please try again."),
                        )
                    }
                }
            // On success, SessionRepository flips auth state and the root nav
            // swaps to the main shell; nothing more to do here.
        }
    }
}
