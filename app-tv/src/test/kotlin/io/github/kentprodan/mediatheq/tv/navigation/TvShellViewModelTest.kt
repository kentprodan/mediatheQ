package io.github.kentprodan.mediatheq.tv.navigation

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.testing.FakeAppLog
import io.github.kentprodan.mediatheq.core.testing.FakeAppModeRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TvShellViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playback = FakePlaybackConnection()
    private val log = FakeAppLog()
    private val appMode = FakeAppModeRepository(initial = AppMode.Music)
    private val viewModel by lazy { TvShellViewModel(playback, log, appMode) }

    @Test
    fun `the drawer follows the app mode, Movies and Series before the music`() =
        runTest(mainDispatcherRule.testDispatcher) {
            assertThat(viewModel.destinations.value)
                .containsExactly(
                    TvDestination.Home,
                    TvDestination.Search,
                    TvDestination.Library,
                    TvDestination.NowPlaying,
                    TvDestination.Settings,
                ).inOrder()

            appMode.stored.value = AppMode.Both
            runCurrent()
            assertThat(viewModel.destinations.value)
                .containsExactly(
                    TvDestination.Home,
                    TvDestination.Search,
                    TvDestination.Movies,
                    TvDestination.Series,
                    TvDestination.Library,
                    TvDestination.NowPlaying,
                    TvDestination.Settings,
                ).inOrder()

            appMode.stored.value = AppMode.Video
            runCurrent()
            assertThat(viewModel.destinations.value)
                .containsExactly(
                    TvDestination.Home,
                    TvDestination.Search,
                    TvDestination.Movies,
                    TvDestination.Series,
                    TvDestination.Settings,
                ).inOrder()
        }

    @Test
    fun `the strip follows the playback state`() {
        val playing = PlayerUiModel(NowPlayingItem(MediaId(ServerId("s"), "t"), "Strobe", "deadmau5"), isPlaying = true)

        playback.state.value = playing

        assertThat(viewModel.player.value).isEqualTo(playing)
    }

    @Test
    fun `a destination without a TV screen is logged as a warning`() {
        viewModel.unknownDestination(AlbumKey(MediaId(ServerId("s"), "a")))

        assertThat(log.lines.single()).startsWith("W TvShell: No TV screen for")
    }
}
