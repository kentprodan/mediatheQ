package io.github.kentprodan.mediatheq.core.playback

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OfflineModeNetworkTest {
    private val network = FakeNetworkMonitor()
    private val offlineMode = MutableStateFlow(false)

    @Test
    fun `with the offline mode setting on, the player sees no network`() =
        runTest(UnconfinedTestDispatcher()) {
            val playerNetwork = OfflineModeNetwork(network, offlineMode, backgroundScope)

            playerNetwork.online.test {
                assertThat(awaitItem()).isTrue()
                assertThat(playerNetwork.isOnline()).isTrue()

                offlineMode.value = true
                assertThat(awaitItem()).isFalse()
                assertThat(playerNetwork.isOnline()).isFalse()

                offlineMode.value = false
                network.online.value = false
                assertThat(playerNetwork.isOnline()).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
