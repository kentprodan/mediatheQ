package io.github.kentprodan.mediatheq.feature.nowplaying

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class QueueScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val queue =
        (1..4).map {
            QueueItem("e$it", MediaId(ServerId("s"), "t$it"), "Track $it", "Artist", duration = 3.minutes)
        }
    private val actions = mutableListOf<NowPlayingAction>()

    private fun setContent(canReorder: Boolean = true) {
        composeRule.setContent {
            MediatheqTheme {
                QueueScreen(
                    queue,
                    currentEntryId = "e2",
                    canReorder = canReorder,
                    onAction = { actions += it },
                    onClose = {},
                )
            }
        }
    }

    private fun row(n: Int): SemanticsNodeInteraction = composeRule.onNode(hasText("Track $n") and hasClickAction())

    private fun SemanticsNodeInteraction.customActionLabels(): List<String> =
        fetchSemanticsNode().config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.map { it.label }

    private fun SemanticsNodeInteraction.runCustomAction(label: String) {
        val actions = fetchSemanticsNode().config.getOrElse(SemanticsActions.CustomActions) { emptyList() }
        composeRule.runOnUiThread { actions.single { it.label == label }.action() }
    }

    @Test
    fun `tapping an entry plays it`() {
        setContent()

        row(3).performClick()

        assertThat(actions).containsExactly(NowPlayingAction.SkipToQueueItem("e3"))
    }

    @Test
    fun `the playing entry says so`() {
        setContent()

        row(2).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Now playing"))
    }

    @Test
    fun `swiping an entry away removes it`() {
        setContent()

        row(3).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertThat(actions).containsExactly(NowPlayingAction.RemoveQueueItem("e3"))
    }

    @Test
    fun `dragging an entry by its handle moves it once, where it is dropped`() {
        setContent()
        val rowHeight =
            row(1)
                .fetchSemanticsNode()
                .size.height
                .toFloat()

        // The handle sits at the end of the row, inside its end padding.
        row(1).performTouchInput { down(Offset(right - 40.dp.toPx(), centerY)) }
        repeat(10) {
            row(1).performTouchInput { moveBy(Offset(0f, rowHeight * 0.25f)) }
            composeRule.waitForIdle()
        }
        row(1).performTouchInput { up() }
        composeRule.waitForIdle()

        assertThat(actions).containsExactly(NowPlayingAction.MoveQueueItem("e1", 2))
    }

    @Test
    fun `a long queue opens at the playing entry, and dragging the top row there moves it without a jump`() {
        val long = (1..30).map { QueueItem("e$it", MediaId(ServerId("s"), "t$it"), "Track $it", "Artist") }
        composeRule.setContent {
            MediatheqTheme {
                QueueScreen(long, currentEntryId = "e20", canReorder = true, onAction = { actions += it }, onClose = {})
            }
        }
        val rowHeight =
            row(20)
                .fetchSemanticsNode()
                .size.height
                .toFloat()
        row(1).assertDoesNotExist()
        row(20).assertIsDisplayed()

        // The top visible row moves down two places.
        row(20).performTouchInput { down(Offset(right - 40.dp.toPx(), centerY)) }
        repeat(10) {
            row(20).performTouchInput { moveBy(Offset(0f, rowHeight * 0.25f)) }
            composeRule.waitForIdle()
        }
        row(20).performTouchInput { up() }
        composeRule.waitForIdle()

        assertThat(actions).containsExactly(NowPlayingAction.MoveQueueItem("e20", 21))
    }

    @Test
    fun `a queue that arrives after the screen opened still scrolls to the playing entry`() {
        val long = (1..30).map { QueueItem("e$it", MediaId(ServerId("s"), "t$it"), "Track $it", "Artist") }
        var shown by mutableStateOf(emptyList<QueueItem>())
        composeRule.setContent {
            MediatheqTheme {
                QueueScreen(shown, currentEntryId = "e20", canReorder = true, onAction = {}, onClose = {})
            }
        }

        shown = long
        composeRule.waitForIdle()

        row(20).assertIsDisplayed()
        row(1).assertDoesNotExist()
    }

    @Test
    fun `TalkBack can move and remove entries with custom actions`() {
        setContent()

        assertThat(row(1).customActionLabels()).containsExactly("Move down", "Remove from queue").inOrder()
        assertThat(row(2).customActionLabels()).containsExactly("Move up", "Move down", "Remove from queue").inOrder()
        assertThat(row(4).customActionLabels()).containsExactly("Move up", "Remove from queue").inOrder()

        row(2).runCustomAction("Move up")
        row(2).runCustomAction("Remove from queue")

        assertThat(actions)
            .containsExactly(NowPlayingAction.MoveQueueItem("e2", 0), NowPlayingAction.RemoveQueueItem("e2"))
            .inOrder()
    }

    @Test
    fun `a shuffled queue cannot be reordered, only trimmed`() {
        setContent(canReorder = false)

        assertThat(row(2).customActionLabels()).containsExactly("Remove from queue")
        row(1).performTouchInput {
            down(Offset(right - 40.dp.toPx(), centerY))
            moveBy(Offset(0f, height * 2f))
            up()
        }
        composeRule.waitForIdle()

        assertThat(actions.filterIsInstance<NowPlayingAction.MoveQueueItem>()).isEmpty()
    }
}
