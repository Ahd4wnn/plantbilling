package com.plantora.billing.data.remote

import com.plantora.billing.data.NetworkMonitor
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges OkHttp outcomes to the [NetworkMonitor]: a returned response means the
 * server is reachable; an [IOException] (DNS failure, refused connection, timeout)
 * means it isn't, which drives the "no internet" popup even when the device claims
 * to be online.
 */
@Singleton
class NetworkReportingInterceptor @Inject constructor(
    private val monitor: NetworkMonitor,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            val response = chain.proceed(chain.request())
            monitor.reportSuccess()
            response
        } catch (e: IOException) {
            monitor.reportFailure()
            throw e
        }
    }
}
