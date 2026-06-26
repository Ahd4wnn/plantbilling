package com.plantora.billing.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.plantora.billing.BuildConfig
import com.plantora.billing.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the app can actually reach the backend. Two signals are combined:
 *  - device connectivity (ConnectivityManager) → catches "no internet";
 *  - request outcomes reported by [NetworkReportingInterceptor] → catches "online
 *    but the server isn't answering".
 * The UI shows a blocking "no internet" popup whenever [isConnected] is false.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val deviceOnline = MutableStateFlow(currentlyOnline())
    /** Flipped by the OkHttp interceptor: true after any successful response, false on a connect failure. */
    private val serverReachable = MutableStateFlow(true)

    val isConnected: StateFlow<Boolean> =
        combine(deviceOnline, serverReachable) { online, reachable -> online && reachable }
            .stateIn(appScope, SharingStarted.Eagerly, true)

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { deviceOnline.value = currentlyOnline() }
            override fun onLost(network: Network) { deviceOnline.value = currentlyOnline() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                deviceOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        })
    }

    private fun currentlyOnline(): Boolean {
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun reportSuccess() { serverReachable.value = true }
    fun reportFailure() { serverReachable.value = false }

    /** Actively re-check reachability (used by the popup's "Try again" button). */
    suspend fun recheck(): Boolean {
        deviceOnline.value = currentlyOnline()
        if (!deviceOnline.value) return false
        val reachable = probeServer()
        serverReachable.value = reachable
        return reachable
    }

    private suspend fun probeServer(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val uri = URI(BuildConfig.DEFAULT_BASE_URL)
            val port = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80
            Socket().use { it.connect(InetSocketAddress(uri.host, port), 5000) }
            true
        }.getOrDefault(false)
    }
}
