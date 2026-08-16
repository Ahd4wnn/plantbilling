package com.plantora.billing.data

import com.plantora.billing.data.local.SavedAccount
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
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: User) : AuthState
    /** Logged in but the role (admin) isn't supported by this app. */
    data class UnsupportedRole(val user: User) : AuthState
}

/** Outcome of tapping a saved account in the login picker. */
sealed interface SwitchResult {
    data object Ok : SwitchResult
    /** The saved token was rejected (expired/revoked) — ask for this account's password. */
    data class NeedsPassword(val email: String) : SwitchResult
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

    /** The device's saved logins, for the login screen's account picker. */
    val savedAccounts: StateFlow<List<SavedAccount>> = tokenStore.accountsFlow

    init {
        appScope.launch { bootstrap() }
        // Any 401 anywhere flips us back to login (the interceptor already stripped the
        // active token). The saved account entry is kept so the user can re-enter it.
        appScope.launch {
            authEvents.unauthorized.collect {
                _state.value = AuthState.Unauthenticated
            }
        }
    }

    /**
     * Restore an existing session on launch. Persistent-login rules:
     *  - If an active account is saved, authenticate IMMEDIATELY from its cached user
     *    so the app opens offline and never bounces to login on a flaky network.
     *  - Then refresh via /auth/me in the background: success updates the cache; a plain
     *    network error is ignored (stay logged in); a genuine 401 is handled by the
     *    interceptor (strips the token, emits unauthorized) — the only thing that logs
     *    the user out is an invalid token or an explicit Log out.
     */
    private suspend fun bootstrap() {
        val active = tokenStore.accounts().firstOrNull { it.email == tokenStore.currentActiveEmail() }
        if (tokenStore.token.isNullOrBlank()) {
            _state.value = AuthState.Unauthenticated
            return
        }
        // Optimistic offline restore from the cached identity, when we have one.
        active?.let { _state.value = it.toUser().toAuthState() }

        runCatching { authApi.me() }
            .onSuccess { me ->
                cache(me)
                _state.value = me.toAuthState()
            }
            .onFailure { e ->
                // 401 → interceptor already logged this account out. Any other failure
                // (offline, 5xx) must NOT clear the session: keep whatever we restored,
                // or, if we had no cache to restore, fall back to Unauthenticated.
                if (e.isUnauthorized()) {
                    _state.value = AuthState.Unauthenticated
                } else if (active == null) {
                    _state.value = AuthState.Unauthenticated
                }
            }
    }

    /** Password login. On success the account is saved (token + cached identity). */
    suspend fun login(email: String, password: String): Result<Unit> {
        return runCatching {
            // Email never contains whitespace; strip any (e.g. autofill newlines).
            val cleanEmail = email.filterNot { it.isWhitespace() }
            val cleanPassword = password.filterNot { it == '\n' || it == '\r' }
            val token = authApi.login(LoginRequestDto(cleanEmail, cleanPassword))
            // Save the token first so the /auth/me call below is authenticated.
            tokenStore.upsert(
                SavedAccount(userId = "", email = cleanEmail, token = token.accessToken, role = ""),
            )
            val me = authApi.me()
            cache(me)
            _state.value = me.toAuthState()
        }.onFailure {
            // A failed login must not leave a half-saved, tokenless entry active.
            tokenStore.clearActive()
        }
    }

    /** One-tap login: switch to an already-saved account using its stored token. */
    suspend fun switchTo(email: String): SwitchResult {
        tokenStore.setActive(email)
        val cached = tokenStore.accounts().firstOrNull { it.email == email }
        if (cached == null || cached.token.isBlank()) return SwitchResult.NeedsPassword(email)
        return runCatching { authApi.me() }
            .fold(
                onSuccess = { me -> cache(me); _state.value = me.toAuthState(); SwitchResult.Ok },
                onFailure = { e ->
                    when {
                        // Bad token → the interceptor cleared it; ask for the password.
                        e.isUnauthorized() -> SwitchResult.NeedsPassword(email)
                        // Offline/other → route from the cached identity (stay usable).
                        else -> { _state.value = cached.toUser().toAuthState(); SwitchResult.Ok }
                    }
                },
            )
    }

    /** Explicit Log out: keep the account saved for one-tap return, drop the session. */
    fun logout() {
        tokenStore.clearActive()
        _state.value = AuthState.Unauthenticated
    }

    /** Forget a saved account entirely (its "Remove" action). */
    fun removeAccount(email: String) {
        val wasActive = tokenStore.currentActiveEmail() == email
        tokenStore.remove(email)
        if (wasActive) _state.value = AuthState.Unauthenticated
    }

    private fun cache(me: CurrentUserDto) {
        tokenStore.upsert(
            SavedAccount(
                userId = me.id,
                email = me.email,
                token = tokenStore.token.orEmpty(),
                role = me.role,
                shopName = me.shopName,
                businessName = me.businessName,
                shopId = me.shopId,
                businessUpi = me.businessUpi,
            ),
        )
    }

    private fun Throwable.isUnauthorized(): Boolean = this is HttpException && code() == 401

    private fun SavedAccount.toUser(): User = User(
        id = userId,
        email = email,
        role = Role.from(role),
        shopId = shopId,
        shopName = shopName,
        businessName = businessName,
        businessUpi = businessUpi,
    )

    private fun User.toAuthState(): AuthState =
        if (canUseApp) AuthState.Authenticated(this) else AuthState.UnsupportedRole(this)

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
        return user.toAuthState()
    }
}
