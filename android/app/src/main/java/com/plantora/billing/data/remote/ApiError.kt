package com.plantora.billing.data.remote

import com.plantora.billing.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Turn any network/API error into a clear, plain-language message suitable for
 * an elderly user — never a raw status code or stack trace. Mirrors the web
 * app's friendlyError (frontend/src/api/client.ts).
 */
fun friendlyError(
    error: Throwable,
    fallback: String = "Something went wrong. Please try again.",
): String = when (error) {
    is IOException -> if (BuildConfig.DEBUG) {
        // Debug-only: surface the real cause (TLS/DNS/timeout) to diagnose.
        "Can't reach the server. [${error.javaClass.simpleName}: ${error.message}]"
    } else {
        "Can't reach the server. Check your internet connection."
    }
    is HttpException -> parseDetail(error) ?: fallback
    else -> fallback
}

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
