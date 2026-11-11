package io.github.kentprodan.mediatheq.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NetworkMonitor] backed by `ConnectivityManager`. No network counts as metered: never assume free data. One instance,
 * and each flow shares one platform callback between its collectors (an app may hold at most 100 network requests).
 */
@Singleton
internal class ConnectivityNetworkMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @ApplicationScope private val scope: CoroutineScope,
    ) : NetworkMonitor {
        private val manager get() = context.getSystemService(ConnectivityManager::class.java)

        override fun isMetered(): Boolean {
            val manager = manager ?: return true
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return true
            return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }

        override fun isOnline(): Boolean {
            val manager = manager ?: return false
            return manager.getNetworkCapabilities(manager.activeNetwork)?.canCarryTraffic() == true
        }

        override val online: Flow<Boolean> =
            callbackFlow {
                val manager = manager
                if (manager == null) {
                    send(false)
                    awaitClose()
                    return@callbackFlow
                }
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            trySend(capabilities.canCarryTraffic())
                        }

                        override fun onLost(network: Network) {
                            trySend(false)
                        }
                    }
                send(isOnline())
                manager.registerDefaultNetworkCallback(callback)
                awaitClose { manager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()
                .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

        override val meteredChanges: Flow<Boolean> =
            callbackFlow {
                val manager = manager
                if (manager == null) {
                    send(true)
                    awaitClose()
                    return@callbackFlow
                }
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            // Only a network that carries traffic: losing one is not a switch to mobile data.
                            if (capabilities.canCarryTraffic()) {
                                trySend(!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                            }
                        }
                    }
                send(isMetered())
                manager.registerDefaultNetworkCallback(callback)
                awaitClose { manager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()
                .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

        // INTERNET means "may carry traffic", not "internet verified" (VALIDATED); LAN and Tailscale have it.
        private fun NetworkCapabilities.canCarryTraffic() = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
