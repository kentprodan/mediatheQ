package io.github.kentprodan.mediatheq.feature.tv.nowplaying

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.QualityKind
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLyricsRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// The ViewModel with a fake connection; the screen on a 1080p TV with a remote (D-pad keys), plain Application.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvNowPlayingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playback = FakePlaybackConnection()
    private val library = FakeLibraryRepository()
    private val userData = FakeUserDataRepository()
    private val lyrics = FakeLyricsRepository()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val track =
        Track(
            id("t"),
            "Strobe",
            emptyList(),
            10.minutes,
            albumTitle = "For Lack of a Better Name",
            audioFormat = AudioFormat("flac", 96_000, 24, 2),
            userData = UserData(isFavorite = true),
        )
    private val queue =
        listOf(QueueItem("e1", id("t"), "Strobe", "deadmau5"), QueueItem("e2", id("u"), "Ghosts", "deadmau5"))

    private fun playing(
        isPlaying: Boolean = true,
        repeat: RepeatMode = RepeatMode.Off,
    ) = PlayerUiModel(
        item = NowPlayingItem(track.id, track.title, "deadmau5"),
        isPlaying = isPlaying,
        duration = 10.minutes,
        isSeekable = true,
        hasNext = true,
        currentEntryId = "e1",
        repeatMode = repeat,
        output = AudioOutputInfo(playedItemId = track.id.toString(), streamKnown = true),
    )

    private fun TestScope.viewModel(): TvNowPlayingViewModel {
        library.tracks.value = listOf(track)
        playback.queue.value = queue
        val viewModel = TvNowPlayingViewModel(playback, library, userData, lyrics)
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `seek steps are 10 s, 30 s while held, and stay inside the track`() {
        assertThat(seekTarget(1.minutes, 10.minutes, forward = true, repeat = 0)).isEqualTo(70.seconds)
        assertThat(seekTarget(1.minutes, 10.minutes, forward = false, repeat = 0)).isEqualTo(50.seconds)
        assertThat(seekTarget(1.minutes, 10.minutes, forward = true, repeat = 3)).isEqualTo(90.seconds)
        assertThat(seekTarget(5.seconds, 10.minutes, forward = false, repeat = 0)).isEqualTo(0.seconds)
        assertThat(seekTarget(595.seconds, 10.minutes, forward = true, repeat = 0)).isEqualTo(10.minutes)
    }

    @Test
    fun `nothing queued is idle, a track shows its album, favorite, quality and the queue`() =
        test {
            val viewModel = viewModel()
            assertThat(viewModel.uiState.value).isEqualTo(TvNowPlayingUiState.Idle)

            playback.state.value = playing()
            runCurrent()

            val state = viewModel.uiState.value as TvNowPlayingUiState.Playing
            assertThat(state.album).isEqualTo("For Lack of a Better Name")
            assertThat(state.isFavorite).isTrue()
            assertThat(state.quality?.kind).isEqualTo(QualityKind.HiResLossless)
            assertThat(state.queue).isEqualTo(queue)
        }

    @Test
    fun `the Dolby Atmos badge shows only while passthrough is confirmed, the notes say why not`() =
        test {
            val atmos =
                track.copy(
                    audioFormat = AudioFormat("eac3", 48_000, channels = 6, spatialFormat = SpatialFormat.DolbyAtmos),
                )
            val viewModel = viewModel()
            // After viewModel(), which queues the stereo track.
            library.tracks.value = listOf(atmos)

            fun state(output: AudioOutputInfo): TvNowPlayingUiState.Playing {
                playback.state.value = playing().copy(output = output)
                runCurrent()
                return viewModel.uiState.value as TvNowPlayingUiState.Playing
            }
            val base = AudioOutputInfo(playedItemId = atmos.id.toString(), streamKnown = true)

            val passing = state(base.copy(atmosPassthrough = true))
            assertThat(passing.quality?.kind).isEqualTo(QualityKind.DolbyAtmos)
            assertThat(passing.atmosDecoded || passing.atmosConverted).isFalse()

            val decoded = state(base.copy(atmosDecoded = true))
            assertThat(decoded.quality).isNull()
            assertThat(decoded.atmosDecoded).isTrue()

            val converted = state(base.copy(convertedKbps = 256, atmosConverted = true))
            assertThat(converted.quality?.kind).isEqualTo(QualityKind.HighQuality)
            assertThat(converted.atmosConverted).isTrue()

            // The next, stereo track: no badge, no note.
            library.tracks.value = listOf(track)
            val stereo = state(AudioOutputInfo(playedItemId = track.id.toString(), streamKnown = true))
            assertThat(stereo.quality?.kind).isEqualTo(QualityKind.HiResLossless)
            assertThat(stereo.atmosDecoded || stereo.atmosConverted).isFalse()
        }

    @Test
    fun `the playing track's lyrics are asked once per track, none and errors included`() =
        test {
            val synced = Lyrics(listOf(LyricLine("one", 0.seconds), LyricLine("two", 5.seconds)))
            lyrics.lyrics[track.id] = synced
            val viewModel = viewModel()
            backgroundScope.launch { viewModel.lyrics.collect() }
            runCurrent()
            assertThat(viewModel.lyrics.value).isEqualTo(TvLyricsUiState.None)

            playback.state.value = playing()
            runCurrent()
            assertThat(viewModel.lyrics.value).isEqualTo(TvLyricsUiState.Content(synced))

            playback.state.value = playing(isPlaying = false)
            runCurrent()
            assertThat(lyrics.calls).containsExactly(track.id)

            lyrics.failure = MqError.Network
            playback.state.value = playing().copy(item = NowPlayingItem(id("u"), "Ghosts", "deadmau5"))
            runCurrent()
            assertThat(viewModel.lyrics.value).isEqualTo(TvLyricsUiState.Error)

            lyrics.failure = null
            playback.state.value = playing().copy(item = NowPlayingItem(id("v"), "FML", "deadmau5"))
            runCurrent()
            assertThat(viewModel.lyrics.value).isEqualTo(TvLyricsUiState.None)
        }

    @Test
    fun `the Lyrics button opens the panel, Back from a line closes it and focus returns to the button`() {
        val plain = Lyrics(listOf(LyricLine("first line"), LyricLine("second line")))
        val playing =
            TvNowPlayingUiState.Playing(title = "Strobe", artist = "deadmau5", isPlaying = true, duration = 10.minutes)
        composeRule.setContent {
            MediatheqTvTheme { TvNowPlayingScreen(playing, 6.seconds, {}, lyrics = TvLyricsUiState.Content(plain)) }
        }
        composeRule.onNodeWithText("second line").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Lyrics").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("second line").requestFocus()
        composeRule.onNodeWithText("second line").assertIsFocused()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("second line").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Lyrics").assertIsFocused()
    }

    @Test
    fun `the panel stays while the next track's lyrics load, and a track without lyrics closes it`() {
        val plain = Lyrics(listOf(LyricLine("first line")))
        val playing = TvNowPlayingUiState.Playing(title = "Strobe", artist = "deadmau5", duration = 10.minutes)
        var lyrics by mutableStateOf<TvLyricsUiState>(TvLyricsUiState.Content(plain))
        composeRule.setContent {
            MediatheqTvTheme { TvNowPlayingScreen(playing, 0.seconds, {}, lyrics = lyrics, lyricsOpen = true) }
        }
        composeRule.onNodeWithText("first line").requestFocus()

        lyrics = TvLyricsUiState.Loading
        composeRule.onNodeWithText("Up next").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Lyrics").assertIsFocused()

        lyrics = TvLyricsUiState.None
        composeRule.onNodeWithText("Up next").assertExists()
    }

    @Test
    fun `after an error the Lyrics button asks again`() =
        test {
            lyrics.failure = MqError.Network
            val viewModel = viewModel()
            backgroundScope.launch { viewModel.lyrics.collect() }
            playback.state.value = playing()
            runCurrent()
            assertThat(viewModel.lyrics.value).isEqualTo(TvLyricsUiState.Error)

            lyrics.failure = null
            viewModel.onAction(TvNowPlayingAction.RetryLyrics)
            runCurrent()

            assertThat(lyrics.calls).containsExactly(track.id, track.id)
            assertThat(viewModel.lyrics.value).isEqualTo(TvLyricsUiState.None)
        }

    @Test
    fun `without lyrics the button is off and the hint says why`() {
        val playing = TvNowPlayingUiState.Playing(title = "Strobe", artist = "deadmau5", duration = 10.minutes)
        composeRule.setContent {
            MediatheqTvTheme { TvNowPlayingScreen(playing, 0.seconds, {}, lyrics = TvLyricsUiState.None) }
        }

        composeRule.onNodeWithContentDescription("Lyrics").assertIsNotEnabled()
        composeRule.onNodeWithText("No lyrics for this song").assertExists()
    }

    @Test
    fun `each control sends its command`() =
        test {
            val viewModel = viewModel()
            playback.state.value = playing(repeat = RepeatMode.All)
            playback.position.value = 2.minutes
            runCurrent()

            viewModel.onAction(TvNowPlayingAction.TogglePlayPause)
            viewModel.onAction(TvNowPlayingAction.Next)
            viewModel.onAction(TvNowPlayingAction.Previous)
            viewModel.onAction(TvNowPlayingAction.ToggleShuffle)
            viewModel.onAction(TvNowPlayingAction.CycleRepeat)
            viewModel.onAction(TvNowPlayingAction.Seek(forward = true))
            viewModel.onAction(TvNowPlayingAction.SkipTo("e2"))
            viewModel.onAction(TvNowPlayingAction.Remove("e2"))
            // The playing entry is not removed.
            viewModel.onAction(TvNowPlayingAction.Remove("e1"))
            viewModel.onAction(TvNowPlayingAction.ToggleFavorite)
            runCurrent()

            assertThat(playback.commands)
                .containsExactly(
                    "togglePlayPause",
                    "next",
                    "previous",
                    "shuffle true",
                    "repeat One",
                    "seekTo 2m 10s",
                    "skipTo e2",
                    "remove e2",
                ).inOrder()
            assertThat(userData.calls).containsExactly("favorite:t:false")
        }

    @Test
    fun `focus starts on pause, Right on the progress row seeks forward, select on the second queue item jumps`() {
        val actions = mutableListOf<TvNowPlayingAction>()
        val state =
            TvNowPlayingUiState.Playing(
                "Strobe",
                "deadmau5",
                isPlaying = true,
                duration = 10.minutes,
                isSeekable = true,
                queue = queue,
                currentEntryId = "e1",
            )
        composeRule.setContent { MediatheqTvTheme { TvNowPlayingScreen(state, 1.minutes, { actions += it }) } }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithContentDescription("Pause").assertIsFocused()

        composeRule.onNodeWithContentDescription("Position").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Ghosts").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(actions)
            .containsExactly(TvNowPlayingAction.Seek(forward = true, repeat = 0), TvNowPlayingAction.SkipTo("e2"))
            .inOrder()
    }

    @Test
    fun `Remove from queue leaves the focus on the row that takes the removed one's place`() {
        val three = queue + QueueItem("e3", id("v"), "Raise Your Weapon", "deadmau5")
        var state by mutableStateOf(
            TvNowPlayingUiState.Playing(
                "Strobe",
                "deadmau5",
                isPlaying = true,
                duration = 10.minutes,
                queue = three,
                currentEntryId = "e1",
            ),
        )
        composeRule.setContent {
            MediatheqTvTheme {
                TvNowPlayingScreen(state, 1.minutes, { action ->
                    if (action is TvNowPlayingAction.Remove) {
                        state = state.copy(queue = state.queue.filterNot { it.entryId == action.entryId })
                    }
                })
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("Ghosts").performSemanticsAction(SemanticsActions.RequestFocus)

        // The remote's menu key, then Remove (#354: the focus went to the drawer).
        composeRule.onRoot().performKeyInput { pressKey(Key.Menu) }
        // The menu is its own window: the key goes to its focused entry.
        composeRule
            .onNodeWithText(
                "Remove from queue",
            ).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText("Raise Your Weapon").assertIsFocused()
    }

    @Test
    fun `no seek while the duration is unknown, a held key seeks every few repeats from its last target`() =
        test {
            val viewModel = viewModel()
            playback.state.value = playing().copy(duration = kotlin.time.Duration.ZERO)
            runCurrent()
            viewModel.onAction(TvNowPlayingAction.Seek(forward = true))
            assertThat(playback.commands).isEmpty()

            playback.state.value = playing()
            playback.position.value = 1.minutes
            runCurrent()
            (0..5).forEach { viewModel.onAction(TvNowPlayingAction.Seek(forward = true, repeat = it)) }

            // 1:00 + 10 s on the press, then + 30 s on the fifth repeat (the position has not caught up yet).
            assertThat(playback.commands).containsExactly("seekTo 1m 10s", "seekTo 1m 40s").inOrder()
        }

    @Test
    fun `when the track cannot be seeked, Right on the progress row moves focus on and seeks nothing`() {
        val actions = mutableListOf<TvNowPlayingAction>()
        val state =
            TvNowPlayingUiState.Playing(
                "Strobe",
                "deadmau5",
                duration = 10.minutes,
                queue = queue,
                currentEntryId = "e1",
            )
        composeRule.setContent { MediatheqTvTheme { TvNowPlayingScreen(state, 1.minutes, { actions += it }) } }
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithContentDescription("Position").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }

        assertThat(actions).isEmpty()
        composeRule.onNodeWithContentDescription("Position").assertIsNotFocused()
    }

    @Test
    fun `the screen stays on while music plays, not when paused`() {
        var state by mutableStateOf(TvNowPlayingUiState.Playing("Strobe", "deadmau5", isPlaying = true))
        composeRule.setContent { MediatheqTvTheme { TvNowPlayingScreen(state, 1.minutes, {}) } }
        val view =
            composeRule.activity.window.decorView
                .findViewById<android.view.View>(android.R.id.content)

        composeRule.waitForIdle()
        assertThat(keptOn(view)).isTrue()
        state = state.copy(isPlaying = false)
        composeRule.waitForIdle()
        assertThat(keptOn(view)).isFalse()
    }

    // The flag is on the compose view inside the content frame.
    private fun keptOn(view: android.view.View): Boolean =
        view.keepScreenOn ||
            (view is android.view.ViewGroup && (0 until view.childCount).any { keptOn(view.getChildAt(it)) })
}
