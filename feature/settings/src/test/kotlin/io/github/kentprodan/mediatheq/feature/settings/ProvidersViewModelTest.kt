package io.github.kentprodan.mediatheq.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.data.ServerCheck
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.ServerInfo
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.testing.FakeAccountRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ProvidersViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val home = Account("mediatheq.db", "Home", "Ana", "http://nas:8096", active = true)
    private val office = Account("mediatheq-2.db", "Office", "Bo", "https://office.org", active = false)
    private val accounts = FakeAccountRepository(listOf(home, office))
    private val sync = FakeLibrarySyncRepository()
    private val playback = FakePlaybackConnection()
    private val viewModel by lazy { ProvidersViewModel(accounts, sync, playback) }

    private suspend fun content() =
        viewModel.uiState.first { it is ProvidersUiState.Content } as ProvidersUiState.Content

    @Test
    fun `the accounts show in order`() =
        runTest {
            assertThat(content()).isEqualTo(ProvidersUiState.Content(listOf(home, office)))
        }

    @Test
    fun `switching while playing pauses first and waits for the queue to be saved`() =
        runTest {
            playback.state.value = PlayerUiModel(isPlaying = true)

            viewModel.onAction(ProvidersAction.MakeActive(office.id))
            advanceUntilIdle()

            assertThat(playback.commands).containsExactly("togglePlayPause")
            assertThat(accounts.activated).containsExactly(office.id)
        }

    @Test
    fun `switching while paused touches no playback`() =
        runTest {
            viewModel.onAction(ProvidersAction.MakeActive(office.id))
            advanceUntilIdle()

            assertThat(playback.commands).isEmpty()
            assertThat(accounts.activated).containsExactly(office.id)
        }

    @Test
    fun `removing the active account pauses like a switch, an inactive one does not`() =
        runTest {
            content()
            playback.state.value = PlayerUiModel(isPlaying = true)

            viewModel.onAction(ProvidersAction.Remove(office.id, deleteDownloads = true))
            advanceUntilIdle()
            assertThat(playback.commands).isEmpty()

            viewModel.onAction(ProvidersAction.Remove(home.id, deleteDownloads = false))
            advanceUntilIdle()
            assertThat(playback.commands).containsExactly("togglePlayPause")
            assertThat(accounts.removed).containsExactly(office.id to true, home.id to false).inOrder()
        }

    @Test
    fun `reorder and sync now reach their repositories`() =
        runTest {
            viewModel.onAction(ProvidersAction.Reorder(listOf(office.id, home.id)))
            viewModel.onAction(ProvidersAction.SyncNow)
            advanceUntilIdle()

            assertThat(content().accounts).containsExactly(office, home).inOrder()
            assertThat(sync.syncCalls).isEqualTo(1)
        }

    @Test
    fun `a saved address closes the edit`() =
        runTest {
            accounts.editResult =
                ServerCheck.Supported("http://100.1.2.3:8096", ServerInfo(ServerId("s"), "Home", "12"))
            viewModel.onAction(ProvidersAction.StartEdit(home.id))
            assertThat(content().edit).isEqualTo(AddressEdit(home.id))

            viewModel.onAction(ProvidersAction.EditAddress(home.id, "100.1.2.3"))
            advanceUntilIdle()

            assertThat(accounts.edited).containsExactly(home.id to "100.1.2.3")
            assertThat(content().edit).isNull()
        }

    @Test
    fun `an address that is not saved says why`() =
        runTest {
            val cases =
                mapOf(
                    ServerCheck.OtherServer to AddressError.OtherServer,
                    ServerCheck.InvalidAddress to AddressError.InvalidAddress,
                    ServerCheck.CleartextNotAllowed to AddressError.CleartextNotAllowed,
                    ServerCheck.Failed(MqError.Network) to AddressError.Unreachable,
                    ServerCheck.Failed(MqError.Unknown(IllegalStateException())) to AddressError.Other,
                )
            cases.forEach { (check, error) ->
                accounts.editResult = check
                viewModel.onAction(ProvidersAction.EditAddress(home.id, "x"))
                advanceUntilIdle()
                assertThat(content().edit).isEqualTo(AddressEdit(home.id, error = error))
            }

            viewModel.onAction(ProvidersAction.DismissEdit)
            assertThat(content().edit).isNull()
        }
}
