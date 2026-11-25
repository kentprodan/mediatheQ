package io.github.kentprodan.mediatheq.feature.tv.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.TrackPicker
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaSegment
import io.github.kentprodan.mediatheq.core.model.SegmentType
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoFormat
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TvPlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val videos = FakeVideoRepository()
    private val playback = FakePlaybackConnection()
    private val preferences = FakeVideoPreferencesRepository()
    private val movie = Video(MediaId(ServerId("s"), "m1"), VideoKind.Movie, "Alien", duration = 100.minutes)

    private fun TestScope.viewModel(
        startMs: Long = 0,
        video: Video = movie,
    ): TvPlayerViewModel {
        videos.movies.value = listOf(video)
        val viewModel =
            TvPlayerViewModel(
                VideoPlayerKey(video.id, startMs),
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
    fun `a video with a known frame rate starts once the display settled, never before (#631)`() =
        test {
            val film = movie.copy(format = VideoFormat("hevc", 3840, 2160, frameRate = 23.976f))
            val viewModel = viewModel(video = film)
            backgroundScope.launch { viewModel.videoFormat.collect {} }
            runCurrent()

            assertThat(viewModel.videoFormat.value).isEqualTo(23.976f to 2160)
            assertThat(playback.videoCalls).isEmpty()

            viewModel.onAction(TvPlayerAction.DisplaySettled)
            runCurrent()
            assertThat(playback.videoCalls).hasSize(1)
        }

    @Test
    fun `a switch while playing pauses, then seeks in place and plays on (#631)`() =
        test {
            val viewModel = viewModel()
            playback.state.value = playing()
            playback.position.value = 20.minutes
            runCurrent()

            viewModel.onAction(TvPlayerAction.DisplaySwitching)
            assertThat(playback.commands).contains("pause")
            playback.state.value = playing(isPlaying = false)
            runCurrent()

            viewModel.onAction(TvPlayerAction.DisplaySettled)
            assertThat(
                playback.commands.takeLast(2),
            ).containsExactly("seekTo ${20.minutes}", "togglePlayPause").inOrder()
        }

    @Test
    fun `each click on the bar's +30 s button seeks (#353)`() =
        test {
            val viewModel = viewModel()
            playback.state.value = playing()
            playback.position.value = 30.minutes
            runCurrent()

            viewModel.onAction(TvPlayerAction.Seek(forward = true))
            playback.position.value = 30.minutes + 30.seconds
            viewModel.onAction(TvPlayerAction.Seek(forward = true))

            assertThat(playback.commands.filter { it.startsWith("seekTo") })
                .containsExactly("seekTo ${30.minutes + 30.seconds}", "seekTo ${31.minutes}")
                .inOrder()
        }

    @Test
    fun `a held key steps on from its last target, also when the remote sends separate presses (#353)`() =
        test {
            val viewModel = viewModel()
            playback.state.value = playing()
            playback.position.value = 30.minutes
            runCurrent()

            // Key-downs 50 ms apart, the position not caught up yet.
            (0..3).forEach { viewModel.onAction(TvPlayerAction.Seek(forward = true, atMs = 1_000L + it * 50L)) }

            // + 30 s on the press, then twice the step 150 ms later from the first target.
            assertThat(playback.commands.filter { it.startsWith("seekTo") })
                .containsExactly("seekTo ${30.minutes + 30.seconds}", "seekTo ${31.minutes + 30.seconds}")
                .inOrder()
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

    @Test
    fun `the Skip intro button shows inside the intro and skips to its end, a chapter seeks (#430)`() =
        test {
            val intro = MediaSegment(SegmentType.Intro, 1.minutes, 2.minutes)
            videos.segments[movie.id] = listOf(intro)
            val viewModel = viewModel()
            backgroundScope.launch { viewModel.skipButton.collect {} }
            playback.state.value = playing()
            playback.position.value = 90.seconds
            runCurrent()

            assertThat(viewModel.skipButton.value).isEqualTo(intro)
            viewModel.onAction(TvPlayerAction.SkipSegment)
            viewModel.onAction(TvPlayerAction.SeekTo(10.minutes))

            assertThat(playback.commands).containsAtLeast("seekTo ${2.minutes}", "seekTo ${10.minutes}").inOrder()
            assertThat(viewModel.skipButton.value).isNull()
        }

    @Test
    fun `still watching is due after 3 episodes in a row or 3 hours without a key (#388)`() {
        assertThat(stillWatchingDue(2, 2.hours)).isFalse()
        assertThat(stillWatchingDue(3, 0.minutes)).isTrue()
        assertThat(stillWatchingDue(1, 3.hours)).isTrue()
    }
}
