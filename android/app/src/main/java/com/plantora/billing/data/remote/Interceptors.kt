package com.plantora.billing.data.remote

import com.plantora.billing.data.local.TokenStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites every request's scheme/host/port to the currently configured backend
 * base URL. Retrofit is built with a throwaway placeholder base URL; this makes
 * the real target runtime-configurable (emulator vs LAN vs prod).
 */
@Singleton
class HostSelectionInterceptor @Inject constructor(
    private val baseUrlProvider: BaseUrlProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = baseUrlProvider.get().toHttpUrlOrNull()
            ?: return chain.proceed(request)

        // Prepend any base-URL path prefix (e.g. "/api") so endpoints declared as
        // "/auth/login" hit "https://host/api/auth/login". Works equally when the
        // base has no prefix (e.g. http://10.0.2.2:8000).
        val basePath = base.encodedPath.trimEnd('/') // "/api" or ""
        val newPath = basePath + request.url.encodedPath

        val newUrl = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(newPath)
            .build()

        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}

/** Attaches the bearer token to every request when present. */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.token
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

/**
 * On a 401 for an authenticated request, drop the active session so the app
 * re-routes to Login (the SessionRepository's auth-state flow observes it).
 *
 * We only drop the ACTIVE pointer — the saved account keeps its token — so a
 * transient 401 (a deploy window, brief clock skew) is recoverable: tapping the
 * account retries `/auth/me` with the stored token and, if that succeeds, logs
 * straight back in with no password. Only a genuinely dead token (the retry also
 * 401s) falls through to the password prompt.
 *
 * A 401 from `/auth/login` is skipped entirely: there it means "wrong password on
 * a login attempt", not "the active session's token died" — clearing on it would
 * wrongly log out whatever account is currently active.
 */
@Singleton
class UnauthorizedInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val authEvents: AuthEventBus,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 401 && !request.url.encodedPath.endsWith("/auth/login")) {
            tokenStore.clearActive()
            authEvents.notifyUnauthorized()
        }
        return response
    }
}
