package com.plantora.billing.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A login the device remembers, so the owner (who may hold two accounts) can tap
 * back in without retyping credentials — Instagram-style. We store the long-lived
 * bearer token plus enough of the user to route the app offline. No password is
 * ever stored; if the token turns out to be invalid we fall back to a password
 * prompt for that one account.
 */
@Serializable
data class SavedAccount(
    val userId: String,
    val email: String,
    val token: String,
    val role: String,
    val shopName: String? = null,
    val businessName: String? = null,
    val shopId: String? = null,
    val businessUpi: String? = null,
) {
    /** What the account picker shows as the account's headline. */
    val displayName: String get() = businessName ?: shopName ?: email
}

/**
 * Stores the saved logins (bearer JWTs + cached identity) at rest using
 * EncryptedSharedPreferences. Falls back to plain SharedPreferences if the
 * keystore is unavailable (very old/odd devices) so login never hard-fails.
 *
 * The single active token is exposed as [token] for the network interceptors,
 * which read it synchronously on OkHttp threads. The full list is exposed
 * reactively as [accountsFlow] for the login screen's account picker.
 *
 * `prefs` is built LAZILY, never in the constructor: the first Keystore
 * `Cipher.init` for the master key can block for several seconds on some OEM
 * devices (e.g. OnePlus). Hilt assembles this object on the main thread when the
 * first ViewModel is injected at composition, so an eager build froze the app on
 * startup (ANR). Deferring it means the Keystore work happens on first token
 * *access* instead — always a background thread. `by lazy` is SYNCHRONIZED, so
 * those callers can't race the initialization.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val prefs: SharedPreferences by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "plantora_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            context.getSharedPreferences("plantora_secure_fallback", Context.MODE_PRIVATE)
        }
    }

    // In-memory cache so the interceptor's `token` reads never touch disk/crypto on
    // the hot path, and so the reactive flow can emit without re-decrypting.
    private val _accounts = MutableStateFlow<List<SavedAccount>>(emptyList())
    val accountsFlow: StateFlow<List<SavedAccount>> = _accounts.asStateFlow()

    private var activeEmail: String? = null
    private var loaded = false

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        // Migrate a pre-multi-account single token, if any, into an anonymous entry so
        // the interceptor keeps working until the next successful /auth/me caches identity.
        val legacy = prefs.getString(KEY_LEGACY_TOKEN, null)
        val list = prefs.getString(KEY_ACCOUNTS, null)
            ?.let { runCatching { json.decodeFromString<List<SavedAccount>>(it) }.getOrNull() }
            ?: emptyList()
        _accounts.value = list
        activeEmail = prefs.getString(KEY_ACTIVE, null)
            ?: legacy?.let { null } // legacy token has no email; handled by [token] fallback
        legacyToken = legacy
        loaded = true
    }

    // Only used to keep a pre-upgrade session alive until it's re-cached or expires.
    private var legacyToken: String? = null

    private fun persist() {
        prefs.edit().apply {
            putString(KEY_ACCOUNTS, json.encodeToString(_accounts.value))
            if (activeEmail == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, activeEmail)
            remove(KEY_LEGACY_TOKEN)
        }.apply()
        legacyToken = null
    }

    // ── Active token, for the interceptors (synchronous) ─────────────────────────
    /** The active account's bearer token, or null when logged out. */
    val token: String?
        get() {
            ensureLoaded()
            return _accounts.value.firstOrNull { it.email == activeEmail }?.token ?: legacyToken
        }

    /** Update just the active account's token (e.g. after a refresh). No-op if none. */
    @Synchronized
    fun updateActiveToken(newToken: String) {
        ensureLoaded()
        val email = activeEmail ?: return
        _accounts.value = _accounts.value.map { if (it.email == email) it.copy(token = newToken) else it }
        persist()
    }

    // ── Account list management ──────────────────────────────────────────────────
    fun accounts(): List<SavedAccount> {
        ensureLoaded()
        return _accounts.value
    }

    fun currentActiveEmail(): String? {
        ensureLoaded()
        return activeEmail
    }

    /** Add or replace an account (matched by email) and make it the active one. */
    @Synchronized
    fun upsert(account: SavedAccount) {
        ensureLoaded()
        _accounts.value = _accounts.value.filterNot { it.email.equals(account.email, ignoreCase = true) } + account
        activeEmail = account.email
        persist()
    }

    /** Point the active session at an already-saved account. */
    @Synchronized
    fun setActive(email: String) {
        ensureLoaded()
        if (_accounts.value.any { it.email == email }) {
            activeEmail = email
            persist()
        }
    }

    /** Log out: keep the saved accounts, just drop the active pointer. */
    @Synchronized
    fun clearActive() {
        ensureLoaded()
        activeEmail = null
        legacyToken = null
        persist()
    }

    /** Forget an account entirely. */
    @Synchronized
    fun remove(email: String) {
        ensureLoaded()
        _accounts.value = _accounts.value.filterNot { it.email == email }
        if (activeEmail == email) activeEmail = null
        persist()
    }

    /** Interceptor 401 hook: the active token is invalid → strip it (keep the entry
     *  so the user can re-enter their password) and drop the active pointer. */
    @Synchronized
    fun clear() {
        ensureLoaded()
        val email = activeEmail
        if (email != null) {
            _accounts.value = _accounts.value.map { if (it.email == email) it.copy(token = "") else it }
        }
        activeEmail = null
        legacyToken = null
        persist()
    }

    private companion object {
        const val KEY_ACCOUNTS = "accounts_json"
        const val KEY_ACTIVE = "active_email"
        const val KEY_LEGACY_TOKEN = "jwt" // pre-multi-account single token
    }
}
