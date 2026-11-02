package io.github.kentprodan.mediatheq

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.data.ThemeMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlaybackError
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.SLOW_START_THRESHOLD
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSessionRepository()
    private val userData = FakeUserDataRepository()
    private val sync = FakeLibrarySyncRepository()
    private val libraryPreferences = FakeLibraryPreferencesRepository()
    private val playback = FakePlaybackConnection()
    private val viewModel by lazy { MainViewModel(repository, userData, sync, libraryPreferences, playback) }

    @Test
    fun `the session is unknown until read, then follows the repository`() =
        runTest {
            assertThat(viewModel.session.value).isNull()

            viewModel.session.test {
                assertThat(awaitItem()).isEqualTo(SessionState.LoggedOut())
                repository.session.value = SessionState.LoggedIn("Home", "Ana")
                assertThat(awaitItem()).isEqualTo(SessionState.LoggedIn("Home", "Ana"))
            }
        }

    @Test
    fun `a refused favorite or rating is offered once, then dismissed`() =
        runTest {
            userData.error.value = MqError.Network
            assertThat(viewModel.userDataError.value).isEqualTo(MqError.Network)

            viewModel.dismissUserDataError()

            assertThat(viewModel.userDataError.value).isNull()
        }

    @Test
    fun `a track that could not play is offered once, then dismissed`() {
        val error = PlaybackError("Strobe", PlaybackError.Reason.NotDownloaded, skipped = true)
        playback.error.value = error
        assertThat(viewModel.playbackError.value).isEqualTo(error)

        viewModel.dismissPlaybackError()

        assertThat(viewModel.playbackError.value).isNull()
    }

    @Test
    fun `local network access granted now syncs the library at once`() =
        runTest {
            viewModel.onLocalNetworkGranted()

            assertThat(sync.syncCalls).isEqualTo(1)
        }

    @Test
    fun `the theme is unknown until read, then follows the settings`() =
        runTest {
            assertThat(viewModel.theme.value).isNull()

            viewModel.theme.test {
                assertThat(awaitItem()).isEqualTo(ThemePreferences())
                libraryPreferences.setTheme(ThemePreferences(ThemeMode.Black))
                assertThat(awaitItem()).isEqualTo(ThemePreferences(ThemeMode.Black))
            }
        }

    @Test
    fun `a slow start is offered once, then dismissed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.slowStart.test {
                assertThat(awaitItem()).isNull()
                playback.state.value = PlayerUiModel(item = strobe, currentEntryId = "e1", isBuffering = true)
                advanceTimeBy(SLOW_START_THRESHOLD + 1.seconds)
                assertThat(awaitItem()).isEqualTo(strobe)

                viewModel.dismissSlowStart()
                assertThat(awaitItem()).isNull()
            }
        }

    @Test
    fun `no slow start message while the setting is off`() =
        runTest(mainDispatcherRule.testDispatcher) {
            libraryPreferences.setNavigation(NavigationPreferences(slowStartMessages = false))
            viewModel.slowStart.test {
                assertThat(awaitItem()).isNull()
                playback.state.value = PlayerUiModel(item = strobe, currentEntryId = "e1", isBuffering = true)
                advanceTimeBy(SLOW_START_THRESHOLD + 1.seconds)
                expectNoEvents()
            }
        }

    private val strobe = NowPlayingItem(MediaId(ServerId("s"), "t1"), "Strobe", "deadmau5")
}
