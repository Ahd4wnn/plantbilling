package com.plantora.billing.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the bearer JWT at rest using EncryptedSharedPreferences. Falls back to
 * plain SharedPreferences if the keystore is unavailable (very old/odd devices)
 * so login never hard-fails; the token is short-lived (12h) regardless.
 *
 * `prefs` is built LAZILY, never in the constructor: the first Keystore
 * `Cipher.init` for the master key can block for several seconds on some OEM
 * devices (e.g. OnePlus). Hilt assembles this object on the main thread when the
 * first ViewModel is injected at composition, so an eager build froze the app on
 * startup (ANR). Deferring it means the Keystore work happens on first token
 * *access* instead — always a background thread (session bootstrap runs on
 * Dispatchers.Default; the auth interceptor runs on OkHttp's threads). `by lazy`
 * is SYNCHRONIZED, so those callers can't race the initialization.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
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

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }.apply()
        }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val KEY_TOKEN = "jwt"
    }
}
