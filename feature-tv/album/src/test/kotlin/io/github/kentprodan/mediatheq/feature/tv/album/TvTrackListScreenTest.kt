package io.github.kentprodan.mediatheq.feature.tv.album

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

// A 1080p TV; plain Application, the page gets fixed state. A remote, not a finger: D-pad keys.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvTrackListScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<TvTrackListAction>()
    private val tracks =
        (1..5).map { Track(MediaId(ServerId("s"), "t$it"), "Song $it", emptyList(), 3.minutes, trackNumber = it) }

    private fun show() {
        val state = TvTrackListUiState.Content(TvTrackListHeader("Album", trackCount = 5), listOf(TvDisc(1, tracks)))
        composeRule.setContent { MediatheqTvTheme { TvTrackListScreen(state, { actions += it }) } }
        composeRule.mainClock.advanceTimeBy(1_000)
    }

    private fun press(key: Key) = composeRule.onRoot().performKeyInput { pressKey(key) }

    @Test
    fun `focus starts on Play, Down enters the list, the third track plays from index 2`() {
        show()

        composeRule.onNodeWithText("Play").assertIsFocused()
        press(Key.DirectionDown)
        composeRule.onNodeWithText("Song 1").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertThat(actions).containsExactly(TvTrackListAction.PlayTrack(2))
    }

    @Test
    fun `the menu key on a track opens Play next, Add to queue and Favorite`() {
        show()
        composeRule.onNodeWithText("Song 2").performSemanticsAction(SemanticsActions.RequestFocus)

        press(Key.Menu)
        listOf(
            "Play next",
            "Add to queue",
            "Add to favorites",
        ).forEach { composeRule.onNodeWithText(it).assertExists() }
        composeRule.onNodeWithText("Add to queue").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithText("Add to queue").performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }

        assertThat(actions).containsExactly(TvTrackListAction.AddToQueue(tracks[1]))
    }

    @Test
    fun `the first track of disc 2 plays from its place in the whole album, a repeated track from its own place`() {
        val discs = listOf(TvDisc(1, tracks.take(3)), TvDisc(2, listOf(tracks[3], tracks[0])))
        val state = TvTrackListUiState.Content(TvTrackListHeader("Album", trackCount = 5), discs)
        composeRule.setContent { MediatheqTvTheme { TvTrackListScreen(state, { actions += it }) } }

        composeRule.onNodeWithText("Song 4").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertThat(actions).containsExactly(TvTrackListAction.PlayTrack(3), TvTrackListAction.PlayTrack(4)).inOrder()
    }

    @Test
    fun `an empty page starts on Favorite`() {
        val state = TvTrackListUiState.Content(TvTrackListHeader("Empty"), listOf(TvDisc(1, emptyList())))
        composeRule.setContent { MediatheqTvTheme { TvTrackListScreen(state, {}) } }
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText("Favorite").assertIsFocused()
    }
}
