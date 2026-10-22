package io.github.kentprodan.mediatheq.feature.playlist

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

// Plain Application: the stateless screen needs no Hilt graph. Tall screen: every row is laid out.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h1600dp")
class PlaylistScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val items =
        listOf(0, 2).map {
            PlaylistItem("e$it", it, Track(MediaId(ServerId("s"), "t$it"), "Track $it", emptyList(), 3.minutes))
        }
    private val content = PlaylistUiState.Content(Playlist(MediaId(ServerId("s"), "p1"), "Drum"), items)

    private fun show(
        state: PlaylistUiState,
        actions: MutableList<PlaylistAction>,
    ) = composeRule.setContent { MediatheqTheme { PlaylistScreen(state, { actions += it }, onBack = {}) } }

    @Test
    fun `a long press starts selecting`() {
        val actions = mutableListOf<PlaylistAction>()
        show(content, actions)

        composeRule.onNodeWithText("Track 2").performTouchInput { longClick() }

        assertThat(actions).containsExactly(PlaylistAction.ToggleSelection(2))
    }

    @Test
    fun `while selecting, taps toggle items and the toolbar removes or clears`() {
        val actions = mutableListOf<PlaylistAction>()
        show(content.copy(selected = setOf(0)), actions)

        composeRule.onNodeWithText("1 selected").assertExists()
        composeRule.onNodeWithText("Track 2").performClick()
        composeRule.onNodeWithContentDescription("Remove from playlist").performClick()
        composeRule.onNodeWithContentDescription("Clear selection").performClick()

        assertThat(actions)
            .containsExactly(
                PlaylistAction.ToggleSelection(2),
                PlaylistAction.RemoveSelected,
                PlaylistAction.ClearSelection,
            ).inOrder()
    }

    @Test
    fun `rows offer move up and move down to TalkBack`() {
        val actions = mutableListOf<PlaylistAction>()
        show(content, actions)

        val row = composeRule.onNode(hasCustomAction("Move down")).fetchSemanticsNode()
        val moveDown = row.config[SemanticsActions.CustomActions].single { it.label == "Move down" }
        composeRule.runOnUiThread { moveDown.action() }

        assertThat(actions).containsExactly(PlaylistAction.Move(0, 2))
    }

    @Test
    fun `a tap plays the item outside selection`() {
        val actions = mutableListOf<PlaylistAction>()
        show(content, actions)

        composeRule.onNodeWithText("Track 0").performClick()

        assertThat(actions).containsExactly(PlaylistAction.PlayItem(0))
    }

    @Test
    fun `a selected row is marked selected`() {
        show(content.copy(selected = setOf(2)), mutableListOf())

        composeRule.onNode(hasText("Track 2") and isSelected()).assertExists()
        composeRule.onNode(hasText("Track 0") and isSelected()).assertDoesNotExist()
    }

    private fun hasCustomAction(label: String) =
        SemanticsMatcher("has custom action $label") { node ->
            node.config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.any { it.label == label }
        }
}
