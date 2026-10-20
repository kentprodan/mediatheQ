package io.github.kentprodan.mediatheq.feature.nowplaying

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class NowPlayingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playback = FakePlaybackConnection()
    private val artwork = Artwork("al1", "tag")
    private val playing =
        PlayerUiModel(
            item =
                NowPlayingItem(
                    MediaId(ServerId("s"), "t1"),
                    "Strobe",
                    "deadmau5",
                    artwork,
                ),
            isPlaying = true,
            duration = 10.minutes,
            isSeekable = true,
            hasNext = true,
            hasPrevious = true,
        )

    @Test
    fun `nothing queued hides the player`() =
        runTest {
            NowPlayingViewModel(playback).uiState.test {
                assertThat(awaitItem()).isEqualTo(NowPlayingUiState.Hidden)
            }
        }

    @Test
    fun `the queue entry shows with its cover and controls, and follows pause`() =
        runTest {
            val viewModel = NowPlayingViewModel(playback)
            viewModel.uiState.test {
                assertThat(awaitItem()).isEqualTo(NowPlayingUiState.Hidden)

                playback.state.value = playing
                assertThat(awaitItem())
                    .isEqualTo(
                        NowPlayingUiState.Playing(
                            title = "Strobe",
                            artist = "deadmau5",
                            artwork = artwork,
                            isPlaying = true,
                            duration = 10.minutes,
                            isSeekable = true,
                            hasNext = true,
                            hasPrevious = true,
                        ),
                    )

                playback.state.value = playing.copy(isPlaying = false)
                assertThat((awaitItem() as NowPlayingUiState.Playing).isPlaying).isFalse()

                playback.state.value = PlayerUiModel()
                assertThat(awaitItem()).isEqualTo(NowPlayingUiState.Hidden)
            }
        }

    @Test
    fun `transport actions reach the player`() {
        val viewModel = NowPlayingViewModel(playback)

        viewModel.onAction(NowPlayingAction.TogglePlayPause)
        viewModel.onAction(NowPlayingAction.Next)
        viewModel.onAction(NowPlayingAction.Previous)

        assertThat(playback.commands).containsExactly("togglePlayPause", "next", "previous").inOrder()
    }

    @Test
    fun `seeking turns the fraction into a position in the current track`() {
        playback.state.value = playing
        val viewModel = NowPlayingViewModel(playback)

        viewModel.onAction(NowPlayingAction.SeekTo(0.25f))
        viewModel.onAction(NowPlayingAction.SeekTo(1.5f))

        assertThat(playback.commands).containsExactly("seekTo ${150.seconds}", "seekTo ${10.minutes}").inOrder()
    }

    @Test
    fun `the playing entry and shuffle reach the queue screen`() =
        runTest {
            playback.state.value = playing.copy(currentEntryId = "e2", shuffle = true)

            NowPlayingViewModel(playback).uiState.test {
                val state = awaitItem() as NowPlayingUiState.Playing
                assertThat(state.currentEntryId).isEqualTo("e2")
                assertThat(state.shuffle).isTrue()
            }
        }

    @Test
    fun `the queue comes from the player`() {
        val queue = listOf(QueueItem("e1", MediaId(ServerId("s"), "t1"), "Strobe", "deadmau5"))
        playback.queue.value = queue

        assertThat(NowPlayingViewModel(playback).queue.value).isEqualTo(queue)
    }

    @Test
    fun `queue actions reach the player by entry`() {
        val viewModel = NowPlayingViewModel(playback)

        viewModel.onAction(NowPlayingAction.SkipToQueueItem("e1"))
        viewModel.onAction(NowPlayingAction.RemoveQueueItem("e2"))
        viewModel.onAction(NowPlayingAction.MoveQueueItem("e3", 0))

        assertThat(playback.commands).containsExactly("skipTo e1", "remove e2", "move e3 0").inOrder()
    }

    @Test
    fun `seeking without a known duration does nothing`() {
        playback.state.value = playing.copy(duration = Duration.ZERO)

        NowPlayingViewModel(playback).onAction(NowPlayingAction.SeekTo(0.5f))

        assertThat(playback.commands).isEmpty()
    }
}
