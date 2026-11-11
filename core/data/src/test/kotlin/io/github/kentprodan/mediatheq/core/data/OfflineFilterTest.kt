package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OfflineFilterTest {
    @Test
    fun `the switch always turns offline mode on`() {
        for (auto in AutoOffline.entries) {
            for (online in listOf(true, false)) {
                for (metered in listOf(true, false)) {
                    assertThat(offlineActive(true, auto, online, metered)).isTrue()
                    assertThat(forcedOffline(true, auto, metered)).isTrue()
                }
            }
        }
    }

    @Test
    fun `no network turns it on unless automatic offline mode is Never`() {
        assertThat(offlineActive(false, AutoOffline.NoNetwork, online = false, metered = true)).isTrue()
        assertThat(offlineActive(false, AutoOffline.NotOnWifi, online = false, metered = true)).isTrue()
        assertThat(offlineActive(false, AutoOffline.Never, online = false, metered = true)).isFalse()
    }

    @Test
    fun `not on Wi-Fi turns it on on a metered network, only in that mode`() {
        assertThat(offlineActive(false, AutoOffline.NotOnWifi, online = true, metered = true)).isTrue()
        assertThat(offlineActive(false, AutoOffline.NotOnWifi, online = true, metered = false)).isFalse()
        assertThat(offlineActive(false, AutoOffline.NoNetwork, online = true, metered = true)).isFalse()
        assertThat(offlineActive(false, AutoOffline.Never, online = true, metered = true)).isFalse()
        assertThat(forcedOffline(false, AutoOffline.NotOnWifi, metered = true)).isTrue()
        assertThat(forcedOffline(false, AutoOffline.NoNetwork, metered = true)).isFalse()
    }

    @Test
    fun `the filter follows the network in Not on Wi-Fi mode`() =
        runTest {
            val preferences = FakeLibraryPreferencesRepository()
            preferences.setAutoOffline(AutoOffline.NotOnWifi)
            val network = FakeNetworkMonitor(metered = false)
            val filter = OfflineFilter(preferences, network) { FakeDownloadRepository() }
            assertThat(filter.active.first()).isFalse()
            assertThat(filter.forced.first()).isFalse()

            // Wi-Fi lost: mobile data (or Tailscale over it) is metered.
            network.metered = true
            assertThat(filter.active.first()).isTrue()
            assertThat(filter.forced.first()).isTrue()

            preferences.setAutoOffline(AutoOffline.NoNetwork)
            assertThat(filter.active.first()).isFalse()
            assertThat(filter.forced.first()).isFalse()
        }
}
