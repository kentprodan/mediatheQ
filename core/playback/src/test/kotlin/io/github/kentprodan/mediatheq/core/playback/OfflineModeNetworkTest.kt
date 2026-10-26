package io.github.kentprodan.mediatheq.core.playback

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
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

    @Test
    fun `before the setting is read, the player waits for it instead of streaming`() =
        runTest {
            val setting = MutableSharedFlow<Boolean>()
            val playerNetwork = OfflineModeNetwork(network, setting, backgroundScope)
            val answer = async(Dispatchers.Default) { playerNetwork.isOnline() }
            runCurrent()

            setting.emit(true)

            assertThat(answer.await()).isFalse()
        }
}
