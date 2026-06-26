package com.plantora.billing.data

import com.plantora.billing.data.local.TokenStore
import com.plantora.billing.data.remote.AuthEventBus
import com.plantora.billing.data.remote.api.AuthApi
import com.plantora.billing.data.remote.dto.CurrentUserDto
import com.plantora.billing.data.remote.dto.LoginRequestDto
import com.plantora.billing.di.ApplicationScope
import com.plantora.billing.domain.Role
import com.plantora.billing.domain.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: User) : AuthState
    /** Logged in but the role (admin) isn't supported by this app. */
    data class UnsupportedRole(val user: User) : AuthState
}

@Singleton
class SessionRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val authEvents: AuthEventBus,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        // The backend is fixed (BuildConfig.DEFAULT_BASE_URL); just bootstrap.
        appScope.launch { bootstrap() }
        // Any 401 anywhere flips us back to login.
        appScope.launch {
            authEvents.unauthorized.collect {
                _state.value = AuthState.Unauthenticated
            }
        }
    }

    /** Restore an existing session by validating the stored token via /auth/me. */
    private suspend fun bootstrap() {
        val token = tokenStore.token
        if (token.isNullOrBlank()) {
            _state.value = AuthState.Unauthenticated
            return
        }
        runCatching { authApi.me() }
            .onSuccess { _state.value = it.toAuthState() }
            .onFailure {
                tokenStore.clear()
                _state.value = AuthState.Unauthenticated
            }
    }

    /** Returns null on success, or a friendly error message on failure. */
    suspend fun login(email: String, password: String): Result<Unit> {
        return runCatching {
            val token = authApi.login(LoginRequestDto(email.trim(), password))
            tokenStore.token = token.accessToken
            val me = authApi.me()
            _state.value = me.toAuthState()
        }.onFailure { tokenStore.clear() }
    }

    fun logout() {
        tokenStore.clear()
        _state.value = AuthState.Unauthenticated
    }

    private fun CurrentUserDto.toAuthState(): AuthState {
        val user = User(
            id = id,
            email = email,
            role = Role.from(role),
            shopId = shopId,
            shopName = shopName,
            businessName = businessName,
            businessUpi = businessUpi,
        )
        return if (user.canUseApp) AuthState.Authenticated(user) else AuthState.UnsupportedRole(user)
    }
}
