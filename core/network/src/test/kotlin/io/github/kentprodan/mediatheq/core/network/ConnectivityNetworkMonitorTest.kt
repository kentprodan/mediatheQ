package io.github.kentprodan.mediatheq.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
// The newest SDK in Robolectric 4.17 has no registerDefaultNetworkCallback(callback) (NoSuchMethodError).
@Config(sdk = [35])
class ConnectivityNetworkMonitorTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ConnectivityManager::class.java)!!
    private val monitor = ConnectivityNetworkMonitor(context)

    @Test
    fun `no active network is offline`() {
        shadowOf(manager).setDefaultNetworkActive(false)
        shadowOf(manager).setActiveNetworkInfo(null)

        assertThat(monitor.isOnline()).isFalse()
    }

    @Test
    fun `a network without validated internet (LAN, Tailscale) is online`() {
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, internet())

        assertThat(monitor.isOnline()).isTrue()
    }

    @Test
    fun `online follows the default network`() =
        runTest {
            shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, internet())

            monitor.online.test {
                assertThat(awaitItem()).isTrue()
                val callback = shadowOf(manager).networkCallbacks.single()
                val network = ShadowNetwork.newInstance(7)

                callback.onLost(network)
                assertThat(awaitItem()).isFalse()

                callback.onCapabilitiesChanged(network, internet())
                assertThat(awaitItem()).isTrue()
            }
        }

    private fun internet() =
        ShadowNetworkCapabilities.newInstance().also {
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
}
