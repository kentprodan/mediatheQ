package io.github.kentprodan.mediatheq.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import javax.inject.Inject

/** [NetworkMonitor] backed by `ConnectivityManager`. No network counts as metered: never assume free data. */
internal class ConnectivityNetworkMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NetworkMonitor {
        override fun isMetered(): Boolean {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return true
            return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
    }
