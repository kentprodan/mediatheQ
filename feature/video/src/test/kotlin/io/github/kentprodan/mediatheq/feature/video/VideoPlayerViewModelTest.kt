package io.github.kentprodan.mediatheq.feature.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeTrackChoiceRepository
import io.github.kentprodan.mediatheq.core.testing.FakeVideoRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class VideoPlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val id = MediaId(ServerId("s"), "m1")
    private val movie = Video(id, VideoKind.Movie, "Moșoi", year = 2019, duration = 90.minutes)
    private val videos = FakeVideoRepository().apply { movies.value = listOf(movie) }
    private val playback = FakePlaybackConnection()
    private val choices = FakeTrackChoiceRepository()
    private val picker = TrackPicker(videos, choices)

    private fun TestScope.viewModel(
        key: VideoPlayerKey = VideoPlayerKey(id, startPositionMs = 60_000),
    ): VideoPlayerViewModel =
        VideoPlayerViewModel(key, videos, playback, picker).also { vm ->
            backgroundScope.launch { vm.uiState.collect() }
            runCurrent()
        }

    private fun playing(
        isPlaying: Boolean = true,
        isBuffering: Boolean = false,
        isFailed: Boolean = false,
    ) {
        playback.state.value =
            PlayerUiModel(
                item = NowPlayingItem(id, "Moșoi", "2019", isVideo = true),
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                duration = 90.minutes,
                isSeekable = true,
                videoAspectRatio = 16 / 9f,
                isFailed = isFailed,
            )
    }

    private fun VideoPlayerViewModel.content() = uiState.value as VideoPlayerUiState.Playing

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `it starts the video at the key's position and shows loading until the player has it`() =
        test {
            val viewModel = viewModel()

            assertThat(playback.videoCalls).containsExactly(movie to 1.minutes)
            assertThat(viewModel.uiState.value).isEqualTo(VideoPlayerUiState.Loading)
        }

    @Test
    fun `opened from the mini player it shows the video as it plays, without starting it again`() =
        test {
            playing()
            val viewModel = viewModel(VideoPlayerKey(id, attach = true))

            assertThat(playback.videoCalls).isEmpty()
            assertThat(viewModel.content().title).isEqualTo("Moșoi")
            assertThat(viewModel.content().subtitle).isEqualTo("2019")
            assertThat(viewModel.content().aspectRatio).isEqualTo(16 / 9f)
        }

    @Test
    fun `playing, paused and buffering follow the player`() =
        test {
            val viewModel = viewModel()
            playing()
            runCurrent()
            assertThat(viewModel.content().isPlaying).isTrue()

            playing(isPlaying = false)
            runCurrent()
            assertThat(viewModel.content().isPlaying).isFalse()

            playing(isBuffering = true)
            runCurrent()
            assertThat(viewModel.content().isBuffering).isTrue()
        }

    @Test
    fun `the controls hide three seconds after the last touch while playing, and stay while paused`() =
        test {
            val viewModel = viewModel()
            playing()
            runCurrent()
            assertThat(viewModel.content().controlsShown).isTrue()

            advanceTimeBy(VideoPlayerViewModel.CONTROLS_TIMEOUT + 1.milliseconds)
            assertThat(viewModel.content().controlsShown).isFalse()

            viewModel.onAction(VideoPlayerAction.ToggleControls)
            advanceTimeBy(2.seconds)
            assertThat(viewModel.content().controlsShown).isTrue()
            viewModel.onAction(VideoPlayerAction.SeekTo(10.minutes))
            advanceTimeBy(2.seconds)
            assertThat(viewModel.content().controlsShown).isTrue()
            advanceTimeBy(1.seconds + 1.milliseconds)
            assertThat(viewModel.content().controlsShown).isFalse()

            playing(isPlaying = false)
            runCurrent()
            assertThat(viewModel.content().controlsShown).isTrue()
        }

    @Test
    fun `a tap on shown controls hides them at once`() =
        test {
            val viewModel = viewModel()
            playing()
            runCurrent()

            viewModel.onAction(VideoPlayerAction.ToggleControls)
            runCurrent()

            assertThat(viewModel.content().controlsShown).isFalse()
        }

    @Test
    fun `play, pause and seek go to the connection, and leaving pauses a playing video`() =
        test {
            val viewModel = viewModel()
            playing()
            runCurrent()

            viewModel.onAction(VideoPlayerAction.TogglePlayPause)
            viewModel.onAction(VideoPlayerAction.SeekTo(10.minutes))
            viewModel.onAction(VideoPlayerAction.Leave)

            assertThat(playback.commands).containsExactly("togglePlayPause", "seekTo 10m", "pause").inOrder()
        }

    @Test
    fun `leaving while the stream still loads pauses, so the video never starts without a screen`() =
        test {
            val viewModel = viewModel()

            viewModel.onAction(VideoPlayerAction.Leave)

            assertThat(playback.commands).containsExactly("pause")
        }

    @Test
    fun `once the video is gone from the player (it ended and the music came back) the player is ended`() =
        test {
            val viewModel = viewModel()
            playing()
            runCurrent()

            playback.state.value = PlayerUiModel()
            runCurrent()

            assertThat(viewModel.uiState.value).isEqualTo(VideoPlayerUiState.Ended)
        }

    @Test
    fun `a video that cannot play, or is not in the library, is an error`() =
        test {
            val viewModel = viewModel()
            playing(isPlaying = false, isFailed = true)
            runCurrent()
            assertThat(viewModel.uiState.value).isInstanceOf(VideoPlayerUiState.Error::class.java)

            val gone = VideoPlayerViewModel(VideoPlayerKey(MediaId(ServerId("s"), "gone")), videos, playback, picker)
            backgroundScope.launch { gone.uiState.collect() }
            runCurrent()
            assertThat(gone.uiState.value).isEqualTo(VideoPlayerUiState.Error(MqError.NotFound))
        }

    @Test
    fun `the countdown runs in the last ten seconds when a next episode is queued`() =
        test {
            val viewModel = viewModel()
            backgroundScope.launch { viewModel.nextIn.collect() }
            playback.state.value =
                PlayerUiModel(
                    item = NowPlayingItem(id, "Moșoi", "", isVideo = true),
                    isPlaying = true,
                    duration = 90.minutes,
                    hasNext = true,
                )
            playback.position.value = 90.minutes - 20.seconds
            runCurrent()
            assertThat(viewModel.nextIn.value).isNull()

            playback.position.value = 90.minutes - 7.5.seconds
            runCurrent()
            assertThat(viewModel.nextIn.value).isEqualTo(8)

            playback.state.value = playback.state.value.copy(hasNext = false)
            runCurrent()
            assertThat(viewModel.nextIn.value).isNull()
        }

    @Test
    fun `Cancel takes the next episode off, Play now skips to it`() =
        test {
            val viewModel = viewModel()

            viewModel.onAction(VideoPlayerAction.CancelNext)
            viewModel.onAction(VideoPlayerAction.PlayNextNow)

            assertThat(playback.commands).containsExactly("cancelNext", "next").inOrder()
        }

    @Test
    fun `when the next episode starts the screen goes on with it instead of ending`() =
        test {
            val next = Video(MediaId(ServerId("s"), "m2"), VideoKind.Episode, "Episode 2")
            videos.movies.value = listOf(movie, next)
            val viewModel = viewModel()
            playing()
            runCurrent()

            playback.state.value =
                PlayerUiModel(item = NowPlayingItem(next.id, "Episode 2", "", isVideo = true), isPlaying = true)
            runCurrent()

            assertThat(viewModel.content().title).isEqualTo("Episode 2")
        }

    @Test
    fun `while the next episode is read the screen loads, it never ends`() =
        test {
            val next = Video(MediaId(ServerId("s"), "m2"), VideoKind.Episode, "Episode 2")
            val viewModel = viewModel()
            playing()
            runCurrent()
            val states = mutableListOf<VideoPlayerUiState>()
            backgroundScope.launch { viewModel.uiState.collect { states += it } }
            runCurrent()

            // The player is on the next episode before Room has it.
            playback.state.value = PlayerUiModel(item = NowPlayingItem(next.id, "Episode 2", "", isVideo = true))
            runCurrent()
            videos.movies.value = listOf(movie, next)
            runCurrent()

            assertThat(states).doesNotContain(VideoPlayerUiState.Ended)
            assertThat(viewModel.content().title).isEqualTo("Episode 2")
        }
}
