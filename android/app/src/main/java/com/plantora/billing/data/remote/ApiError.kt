package com.plantora.billing.data.remote

import android.content.Context
import androidx.annotation.StringRes
import com.plantora.billing.BuildConfig
import com.plantora.billing.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException
// SocketTimeoutException (per-read/write) and OkHttp's callTimeout failure both
// extend InterruptedIOException, so one branch catches every kind of "too slow".
import java.io.InterruptedIOException

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Turn any network/API error into a clear, plain-language message suitable for
 * an elderly user — never a raw status code or stack trace. Mirrors the web
 * app's friendlyError (frontend/src/api/client.ts).
 */
fun friendlyError(
    error: Throwable,
    fallback: String = "Something went wrong. Please try again.",
): String = when {
    BuildConfig.DEBUG && error is IOException ->
        // Debug-only: surface the real cause (TLS/DNS/timeout) to diagnose.
        "Can't reach the server. [${error.javaClass.simpleName}: ${error.message}]"
    error is InterruptedIOException ->
        "This is taking too long. Please check your connection and try again."
    error is IOException -> "Can't reach the server. Check your internet connection."
    error is HttpException -> parseDetail(error) ?: oversizeMessage(error) ?: fallback
    else -> fallback
}

/**
 * Locale-aware [friendlyError]. Resolves the network-failure and generic
 * fallback text from resources using [context], so error messages surfaced this
 * way follow the app's chosen language. Used by `UiText.Err`, which hands in the
 * locale-scoped activity context from the composition. The pure [String] overload
 * above stays for callers without a Context (their fallbacks remain English).
 */
fun friendlyError(
    context: Context,
    error: Throwable,
    @StringRes fallback: Int = R.string.err_generic,
): String = when {
    BuildConfig.DEBUG && error is IOException ->
        "Can't reach the server. [${error.javaClass.simpleName}: ${error.message}]"
    error is InterruptedIOException -> context.getString(R.string.err_timeout)
    error is IOException -> context.getString(R.string.err_network)
    error is HttpException ->
        parseDetail(error)
            ?: error.takeIf { it.code() == HTTP_PAYLOAD_TOO_LARGE }?.let { context.getString(R.string.err_upload_too_large) }
            ?: context.getString(fallback)
    else -> context.getString(fallback)
}

/**
 * A 413 that carried no JSON `detail` came from the reverse proxy, not from us —
 * its body is HTML, so [parseDetail] finds nothing and the user would otherwise
 * get the useless generic message. Name the real problem instead.
 */
private fun oversizeMessage(error: HttpException): String? =
    if (error.code() == HTTP_PAYLOAD_TOO_LARGE) {
        "That photo is too large to upload. Please try a different photo."
    } else {
        null
    }

private const val HTTP_PAYLOAD_TOO_LARGE = 413

private fun parseDetail(error: HttpException): String? {
    val raw = error.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val obj = errorJson.parseToJsonElement(raw).jsonObject
        when (val detail = obj["detail"]) {
            is JsonPrimitive -> detail.content
            is JsonArray -> (detail.firstOrNull() as? JsonObject)
                ?.get("msg")?.let { (it as? JsonPrimitive)?.content }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
