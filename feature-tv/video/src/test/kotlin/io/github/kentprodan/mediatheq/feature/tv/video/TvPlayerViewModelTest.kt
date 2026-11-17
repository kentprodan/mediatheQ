package io.github.kentprodan.mediatheq.feature.tv.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.TrackPicker
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.model.VideoQuality
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeTrackChoiceRepository
import io.github.kentprodan.mediatheq.core.testing.FakeVideoPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeVideoRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TvPlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val videos = FakeVideoRepository()
    private val playback = FakePlaybackConnection()
    private val preferences = FakeVideoPreferencesRepository()
    private val movie = Video(MediaId(ServerId("s"), "m1"), VideoKind.Movie, "Alien", duration = 100.minutes)

    private fun TestScope.viewModel(startMs: Long = 0): TvPlayerViewModel {
        videos.movies.value = listOf(movie)
        val viewModel =
            TvPlayerViewModel(
                VideoPlayerKey(movie.id, startMs),
                videos,
                playback,
                TrackPicker(videos, FakeTrackChoiceRepository()),
                preferences,
            )
        backgroundScope.launch { viewModel.uiState.collect {} }
        backgroundScope.launch { viewModel.nextIn.collect {} }
        runCurrent()
        return viewModel
    }

    private fun playing(
        hasNext: Boolean = false,
        isPlaying: Boolean = true,
    ) = PlayerUiModel(
        NowPlayingItem(movie.id, movie.title, "", isVideo = true),
        isPlaying = isPlaying,
        duration = 100.minutes,
        hasNext = hasNext,
    )

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `resume starts 10 s before the saved point, seeks go through the session`() =
        test {
            val viewModel = viewModel(startMs = 30.minutes.inWholeMilliseconds)
            assertThat(playback.videoCalls.single().second).isEqualTo(30.minutes - 10.seconds)

            playback.state.value = playing()
            playback.position.value = 30.minutes
            runCurrent()
            viewModel.onAction(TvPlayerAction.Seek(forward = true))

            assertThat(playback.commands).contains("seekTo ${30.minutes + 30.seconds}")
        }

    @Test
    fun `the next episode counts down in the last 10 s, and the screen ends when the video stops`() =
        test {
            val viewModel = viewModel()
            playback.state.value = playing(hasNext = true)
            playback.position.value = 100.minutes - 7.seconds
            runCurrent()
            assertThat(viewModel.nextIn.value).isEqualTo(7)

            playback.state.value = PlayerUiModel()
            runCurrent()
            assertThat(viewModel.uiState.value).isEqualTo(TvPlayerUiState.Ended)
        }

    @Test
    fun `in Video mode the ended video stays the item and the screen still ends`() =
        test {
            val viewModel = viewModel()
            playback.state.value = playing()
            runCurrent()
            assertThat(viewModel.uiState.value).isInstanceOf(TvPlayerUiState.Playing::class.java)

            playback.state.value = playing(isPlaying = false).copy(isEnded = true)
            runCurrent()

            assertThat(viewModel.uiState.value).isEqualTo(TvPlayerUiState.Ended)
        }

    @Test
    fun `a quality chosen in the player is kept and the stream opens again with it`() =
        test {
            val viewModel = viewModel()
            playback.state.value = playing()
            runCurrent()

            viewModel.onAction(TvPlayerAction.SetQuality(VideoQuality.P720))
            runCurrent()

            assertThat(preferences.preferences.value.unmeteredQuality).isEqualTo(VideoQuality.P720)
            assertThat(playback.commands).contains("reloadVideo")
        }
}
