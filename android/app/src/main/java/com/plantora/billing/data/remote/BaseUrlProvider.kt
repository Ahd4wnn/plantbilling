package com.plantora.billing.data.remote

import com.plantora.billing.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the current backend base URL in memory so an OkHttp interceptor can swap
 * the request host at runtime without rebuilding Retrofit. Kept in sync with
 * AppPreferences by the app on startup and whenever Settings changes it.
 */
@Singleton
class BaseUrlProvider @Inject constructor() {
    private val current = AtomicReference(BuildConfig.DEFAULT_BASE_URL)

    fun get(): String = current.get()

    fun set(url: String) {
        if (url.toHttpUrlOrNull() != null) current.set(url)
    }
}
