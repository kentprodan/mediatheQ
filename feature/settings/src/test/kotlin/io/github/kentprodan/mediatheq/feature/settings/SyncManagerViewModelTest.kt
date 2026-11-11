package io.github.kentprodan.mediatheq.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.data.AutoOffline
import io.github.kentprodan.mediatheq.core.data.OfflineFilter
import io.github.kentprodan.mediatheq.core.data.ServerCheck
import io.github.kentprodan.mediatheq.core.data.SyncRecord
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.ServerInfo
import io.github.kentprodan.mediatheq.core.testing.FakeAccountRepository
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import io.github.kentprodan.mediatheq.core.testing.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SyncManagerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = TestClock()
    private val record = SyncRecord(clock.now - 5.minutes, null, 3120, 24.seconds)
    private val home = Account("mediatheq.db", "Home", "Ana", "http://nas:8096", active = true, lastSync = record)
    private val office = Account("mediatheq-2.db", "Office", "Bo", "https://office.org", active = false)
    private val accounts = FakeAccountRepository(listOf(home, office))
    private val sync = FakeLibrarySyncRepository()
    private val session = FakeSessionRepository().apply { serverCheck = SUPPORTED }
    private val network = FakeNetworkMonitor()
    private val preferences = FakeLibraryPreferencesRepository()
    private val viewModel by lazy {
        SyncManagerViewModel(
            accounts,
            sync,
            session,
            network,
            OfflineFilter(preferences, network) { FakeDownloadRepository() },
            clock,
        )
    }

    /** The state once the servers answered; collecting keeps it live like the sheet does. */
    private suspend fun TestScope.content(): SyncManagerUiState.Content {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel.uiState.first { it is SyncManagerUiState.Content } as SyncManagerUiState.Content
    }

    @Test
    fun `idle shows each account's last sync, read back after a restart`() =
        runTest {
            val content = content()

            assertThat(content.cards.map { it.id }).containsExactly(home.id, office.id).inOrder()
            assertThat(content.cards[0].last).isEqualTo(record)
            assertThat(content.cards[0].ago).isEqualTo(5.minutes)
            assertThat(content.cards[1].last).isNull()
            assertThat(content.offline).isNull()
            assertThat(content.syncing).isFalse()
        }

    @Test
    fun `every server is asked and shows online when it answers`() =
        runTest {
            val content = content()

            assertThat(session.checkedAddresses).containsExactly(home.baseUrl, office.baseUrl)
            assertThat(content.cards.map { it.connection }).containsExactly(Connection.Online, Connection.Online)
        }

    @Test
    fun `a running sync shows its step and progress on the active card`() =
        runTest {
            sync.status.value = SyncStatus.Running(SyncStep.Tracks, 500, 1200)
            val content = content()

            assertThat(content.cards[0].running).isEqualTo(SyncProgress(SyncStep.Tracks, 500, 1200))
            assertThat(content.cards[1].running).isNull()
            assertThat(content.syncing).isTrue()
        }

    @Test
    fun `a failed sync shows its error`() =
        runTest {
            val failed = SyncRecord(clock.now, MqError.Server(503), 0, 2.seconds)
            accounts.accounts.update { list -> list.map { if (it.active) it.copy(lastSync = failed) else it } }

            assertThat(content().cards[0].last?.error).isEqualTo(MqError.Server(503))
        }

    @Test
    fun `Sync all syncs the active account`() =
        runTest {
            viewModel.onAction(SyncManagerAction.SyncAll)
            advanceUntilIdle()

            assertThat(sync.syncCalls).isEqualTo(1)
        }

    @Test
    fun `without a network every card is offline and no server is asked`() =
        runTest {
            network.online.value = false
            val content = content()

            assertThat(content.offline).isEqualTo(OfflineReason.NoNetwork)
            assertThat(content.cards.map { it.connection }).containsExactly(Connection.Offline, Connection.Offline)
            assertThat(session.checkedAddresses).isEmpty()
        }

    @Test
    fun `offline mode disables Sync all with its reason`() =
        runTest {
            preferences.preferences.update { it.copy(offlineMode = true) }

            assertThat(content().offline).isEqualTo(OfflineReason.OfflineMode)
            assertThat(session.checkedAddresses).isEmpty()
        }

    @Test
    fun `automatic offline mode on a metered network counts as offline mode`() =
        runTest {
            preferences.preferences.update { it.copy(autoOffline = AutoOffline.NotOnWifi) }
            network.metered = true

            assertThat(content().offline).isEqualTo(OfflineReason.OfflineMode)
        }

    @Test
    fun `an active server that does not answer disables Sync all`() =
        runTest {
            session.serverCheck = ServerCheck.Failed(MqError.Network)
            val content = content()

            assertThat(content.cards[0].connection).isEqualTo(Connection.Offline)
            assertThat(content.offline).isEqualTo(OfflineReason.Unreachable)
        }

    @Test
    fun `Retry asks the server again`() =
        runTest {
            session.serverCheck = ServerCheck.Failed(MqError.Network)
            content()
            session.serverCheck = SUPPORTED

            viewModel.onAction(SyncManagerAction.Retry(home.id))
            advanceUntilIdle()

            val content = viewModel.uiState.value as SyncManagerUiState.Content
            assertThat(content.cards[0].connection).isEqualTo(Connection.Online)
            assertThat(content.offline).isNull()
        }

    private companion object {
        val SUPPORTED = ServerCheck.Supported("http://nas:8096", ServerInfo(ServerId("server-1"), "Home", "10.11.0"))
    }
}
