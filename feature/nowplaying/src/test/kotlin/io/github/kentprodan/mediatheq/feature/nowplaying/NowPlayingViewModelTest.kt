package io.github.kentprodan.mediatheq.feature.nowplaying

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import io.github.kentprodan.mediatheq.core.testing.FakeAudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
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
    private val library = FakeLibraryRepository()
    private val userData = FakeUserDataRepository()
    private val audioPreferences = FakeAudioPreferencesRepository()
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
            NowPlayingViewModel(playback, library, userData, audioPreferences).uiState.test {
                assertThat(awaitItem()).isEqualTo(NowPlayingUiState.Hidden)
            }
        }

    @Test
    fun `the queue entry shows with its cover and controls, and follows pause`() =
        runTest {
            val viewModel = NowPlayingViewModel(playback, library, userData, audioPreferences)
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
        val viewModel = NowPlayingViewModel(playback, library, userData, audioPreferences)

        viewModel.onAction(NowPlayingAction.TogglePlayPause)
        viewModel.onAction(NowPlayingAction.Next)
        viewModel.onAction(NowPlayingAction.Previous)

        assertThat(playback.commands).containsExactly("togglePlayPause", "next", "previous").inOrder()
    }

    @Test
    fun `seeking turns the fraction into a position in the current track`() {
        playback.state.value = playing
        val viewModel = NowPlayingViewModel(playback, library, userData, audioPreferences)

        viewModel.onAction(NowPlayingAction.SeekTo(0.25f))
        viewModel.onAction(NowPlayingAction.SeekTo(1.5f))

        assertThat(playback.commands).containsExactly("seekTo ${150.seconds}", "seekTo ${10.minutes}").inOrder()
    }

    @Test
    fun `the playing entry and shuffle reach the queue screen`() =
        runTest {
            playback.state.value = playing.copy(currentEntryId = "e2", shuffle = true)

            NowPlayingViewModel(playback, library, userData, audioPreferences).uiState.test {
                val state = awaitItem() as NowPlayingUiState.Playing
                assertThat(state.currentEntryId).isEqualTo("e2")
                assertThat(state.shuffle).isTrue()
            }
        }

    @Test
    fun `the queue comes from the player`() {
        val queue = listOf(QueueItem("e1", MediaId(ServerId("s"), "t1"), "Strobe", "deadmau5"))
        playback.queue.value = queue

        assertThat(NowPlayingViewModel(playback, library, userData, audioPreferences).queue.value).isEqualTo(queue)
    }

    @Test
    fun `queue actions reach the player by entry`() {
        val viewModel = NowPlayingViewModel(playback, library, userData, audioPreferences)

        viewModel.onAction(NowPlayingAction.SkipToQueueItem("e1"))
        viewModel.onAction(NowPlayingAction.RemoveQueueItem("e2"))
        viewModel.onAction(NowPlayingAction.MoveQueueItem("e3", 0))

        assertThat(playback.commands).containsExactly("skipTo e1", "remove e2", "move e3 0").inOrder()
    }

    @Test
    fun `seeking without a known duration does nothing`() {
        playback.state.value = playing.copy(duration = Duration.ZERO)

        NowPlayingViewModel(playback, library, userData, audioPreferences).onAction(NowPlayingAction.SeekTo(0.5f))

        assertThat(playback.commands).isEmpty()
    }

    private val track = Track(MediaId(ServerId("s"), "t1"), "Strobe", emptyList(), 10.minutes)

    @Test
    fun `favorite and stars of the playing track come from the library`() =
        runTest {
            library.tracks.value = listOf(track.copy(userData = UserData(isFavorite = true, rating = 8.0)))
            playback.state.value = playing

            NowPlayingViewModel(playback, library, userData, audioPreferences).uiState.test {
                val state = awaitItem() as NowPlayingUiState.Playing
                assertThat(state.isFavorite).isTrue()
                assertThat(state.stars).isEqualTo(4)

                library.tracks.value = listOf(track.copy(userData = UserData(isFavorite = false)))
                val changed = awaitItem() as NowPlayingUiState.Playing
                assertThat(changed.isFavorite).isFalse()
                assertThat(changed.stars).isNull()
            }
        }

    @Test
    fun `a track not in the library shows as not favorite and unrated`() =
        runTest {
            playback.state.value = playing

            NowPlayingViewModel(playback, library, userData, audioPreferences).uiState.test {
                val state = awaitItem() as NowPlayingUiState.Playing
                assertThat(state.isFavorite).isFalse()
                assertThat(state.stars).isNull()
            }
        }

    @Test
    fun `favorite and rating change the playing track`() =
        runTest {
            playback.state.value = playing
            val viewModel = NowPlayingViewModel(playback, library, userData, audioPreferences)

            viewModel.onAction(NowPlayingAction.SetFavorite(true))
            viewModel.onAction(NowPlayingAction.Rate(3))
            viewModel.onAction(NowPlayingAction.Rate(null))

            assertThat(userData.calls).containsExactly("favorite:t1:true", "stars:t1:3", "stars:t1:null").inOrder()
        }

    @Test
    fun `with nothing playing, favorite and rating do nothing`() {
        val viewModel = NowPlayingViewModel(playback, library, userData, audioPreferences)

        viewModel.onAction(NowPlayingAction.SetFavorite(true))
        viewModel.onAction(NowPlayingAction.Rate(3))

        assertThat(userData.calls).isEmpty()
    }

    @Test
    fun `bit-perfect output shows its sample rate on the player`() =
        runTest {
            val viewModel = NowPlayingViewModel(playback, library, userData, audioPreferences)
            playback.state.value = playing.copy(output = AudioOutputInfo(bitPerfectSampleRateHz = 96_000))

            viewModel.uiState.test {
                assertThat((awaitItem() as NowPlayingUiState.Playing).bitPerfectSampleRateHz).isEqualTo(96_000)
            }
        }

    @Test
    fun `the audio screen shows the settings and the output`() =
        runTest {
            val viewModel = NowPlayingViewModel(playback, library, userData, audioPreferences)
            viewModel.audio.test {
                assertThat(awaitItem()).isEqualTo(AudioUiState())

                val output = AudioOutputInfo(bitPerfectSampleRateHz = 44_100, usbDacConnected = true)
                playback.state.value = playing.copy(output = output)
                assertThat(awaitItem().output).isEqualTo(output)
            }
        }

    @Test
    fun `audio actions change the audio settings`() =
        runTest {
            val viewModel = NowPlayingViewModel(playback, library, userData, audioPreferences)

            viewModel.onAction(NowPlayingAction.SetBitPerfectUsb(true))
            viewModel.onAction(NowPlayingAction.SetEqualizerOn(true))
            viewModel.onAction(NowPlayingAction.SetEqPreset(EqPreset.Vocal))
            viewModel.onAction(NowPlayingAction.SetEqBand(index = 0, gainDb = 5f))

            assertThat(audioPreferences.preferences.value)
                .isEqualTo(
                    AudioPreferences(
                        bitPerfectUsb = true,
                        equalizerOn = true,
                        eqPreset = EqPreset.Custom,
                        customGainsDb = listOf(5f, 0f, 4f, 3f, 0f),
                    ),
                )
        }
}
