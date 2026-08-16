package com.plantora.billing.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.R
import com.plantora.billing.data.SessionRepository
import com.plantora.billing.data.SwitchResult
import com.plantora.billing.data.local.SavedAccount
import com.plantora.billing.i18n.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val savedAccounts: List<SavedAccount> = emptyList(),
    /** Show the email/password form instead of the account picker. */
    val showForm: Boolean = false,
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    /** The account whose one-tap login is in flight (its row shows a spinner). */
    val switchingEmail: String? = null,
    val error: UiText? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !submitting
    /** With no saved logins there's nothing to pick — go straight to the form. */
    val formVisible: Boolean get() = showForm || savedAccounts.isEmpty()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val session: SessionRepository,
) : ViewModel() {

    private val _local = MutableStateFlow(LoginUiState())

    val ui: StateFlow<LoginUiState> =
        combine(_local, session.savedAccounts) { local, accounts ->
            local.copy(savedAccounts = accounts)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoginUiState())

    // Strip whitespace/newlines — autofill sometimes injects a trailing U+000A,
    // which the email field's singleLine filter doesn't catch (autofill sets the
    // value directly), and which the backend rejects as an invalid email.
    fun onEmailChange(value: String) =
        _local.update { it.copy(email = value.filterNot(Char::isWhitespace), error = null) }
    fun onPasswordChange(value: String) =
        _local.update { it.copy(password = value.filterNot { c -> c == '\n' || c == '\r' }, error = null) }

    /** Reveal the email/password form ("Log into another account"). */
    fun showLoginForm() = _local.update { it.copy(showForm = true, email = "", password = "", error = null) }

    /** Go back to the saved-accounts picker (only meaningful when some exist). */
    fun showSavedAccounts() = _local.update { it.copy(showForm = false, email = "", password = "", error = null) }

    /** Tap a saved account: one-tap login, or fall back to its password if the token died. */
    fun selectAccount(email: String) {
        if (_local.value.switchingEmail != null) return
        _local.update { it.copy(switchingEmail = email, error = null) }
        viewModelScope.launch {
            when (val result = session.switchTo(email)) {
                is SwitchResult.Ok -> _local.value = LoginUiState()
                is SwitchResult.NeedsPassword -> _local.update {
                    it.copy(
                        switchingEmail = null,
                        showForm = true,
                        email = result.email,
                        password = "",
                        error = UiText.res(R.string.login_session_expired),
                    )
                }
            }
        }
    }

    fun removeAccount(email: String) = session.removeAccount(email)

    fun submit() {
        val state = _local.value
        if (!state.canSubmit) return
        _local.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            session.login(state.email, state.password)
                .onSuccess {
                    // This VM is Activity-scoped, so it survives a later logout and is
                    // reused on the next visit. Reset to a clean slate — otherwise
                    // `submitting` stays true and the button is permanently greyed out
                    // after logging back in. (Also clears the password from memory.)
                    _local.value = LoginUiState()
                }
                .onFailure { e ->
                    _local.update {
                        it.copy(
                            submitting = false,
                            error = UiText.err(e, R.string.err_signin),
                        )
                    }
                }
        }
    }
}
