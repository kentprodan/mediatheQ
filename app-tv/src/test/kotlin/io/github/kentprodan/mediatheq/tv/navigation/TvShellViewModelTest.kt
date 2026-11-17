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

    @Test
    fun `a movie above the limit waits for the PIN, a wrong one keeps waiting, then it opens once (#434)`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val store =
                io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore(
                    io.github.kentprodan.mediatheq.core.testing
                        .InMemoryPreferencesDataStore(),
                    activeId = "kid",
                )
            val parental =
                io.github.kentprodan.mediatheq.core.data
                    .ParentalControls(store)
            parental.setPin("2580")
            parental.setLimit("kid", 12)
            val id =
                io.github.kentprodan.mediatheq.core.model.MediaId(
                    io.github.kentprodan.mediatheq.core.model
                        .ServerId("s"),
                    "m1",
                )
            val videos =
                io.github.kentprodan.mediatheq.core.testing
                    .FakeVideoRepository()
            videos.movies.value =
                listOf(
                    io.github.kentprodan.mediatheq.core.model.Video(
                        id,
                        io.github.kentprodan.mediatheq.core.model.VideoKind.Movie,
                        "Alien",
                        officialRating = "R",
                    ),
                )
            val shell = TvShellViewModel(playback, log, appMode, null, null, parental, videos)
            var opened = 0
            val key =
                io.github.kentprodan.mediatheq.core.navigation
                    .MovieKey(id)

            shell.guard(key) { opened++ }
            runCurrent()
            assertThat(opened).isEqualTo(0)
            shell.submitPin("1111")
            runCurrent()
            assertThat(shell.pinRequest.value?.wrong).isTrue()
            shell.submitPin("2580")
            runCurrent()
            assertThat(opened).isEqualTo(1)
            assertThat(shell.pinRequest.value).isNull()

            // Unlocked for the session: its page again (or its player) does not ask.
            shell.guard(key) { opened++ }
            runCurrent()
            assertThat(opened).isEqualTo(2)
        }
}
