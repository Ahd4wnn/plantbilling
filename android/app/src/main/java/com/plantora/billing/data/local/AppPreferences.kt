package com.plantora.billing.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.plantora.billing.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "plantora_prefs")

/**
 * Non-secret app settings: backend base URL and printer preferences. The cached
 * user and JWT live elsewhere (TokenStore / session). Backed by DataStore.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    val baseUrl: Flow<String> = store.data.map { it[KEY_BASE_URL] ?: BuildConfig.DEFAULT_BASE_URL }

    suspend fun setBaseUrl(url: String) {
        store.edit { it[KEY_BASE_URL] = normalizeBaseUrl(url) }
    }

    // Printer preferences (used from Phase 2 onward).
    val lastPrinterMac: Flow<String?> = store.data.map { it[KEY_PRINTER_MAC] }
    val paperWidthChars: Flow<Int> = store.data.map { it[KEY_PAPER_WIDTH] ?: 32 }
    val autoCut: Flow<Boolean> = store.data.map { it[KEY_AUTO_CUT] ?: true }

    suspend fun setLastPrinterMac(mac: String?) {
        store.edit { p -> if (mac == null) p.remove(KEY_PRINTER_MAC) else p[KEY_PRINTER_MAC] = mac }
    }

    suspend fun setPaperWidthChars(chars: Int) {
        store.edit { it[KEY_PAPER_WIDTH] = chars }
    }

    suspend fun setAutoCut(enabled: Boolean) {
        store.edit { it[KEY_AUTO_CUT] = enabled }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_PRINTER_MAC = stringPreferencesKey("printer_mac")
        val KEY_PAPER_WIDTH = intPreferencesKey("paper_width_chars")
        val KEY_AUTO_CUT = booleanPreferencesKey("auto_cut")
    }
}

/** Ensure a usable base URL: trims, adds scheme, strips trailing slash. */
fun normalizeBaseUrl(raw: String): String {
    var url = raw.trim()
    if (url.isEmpty()) return BuildConfig.DEFAULT_BASE_URL
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        url = "http://$url"
    }
    return url.trimEnd('/')
}
