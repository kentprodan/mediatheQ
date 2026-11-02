package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val track = PreviewData.track
    private var clicks = 0
    private val queued = mutableListOf<String>()

    private fun setContent() {
        composeRule.setContent {
            MediatheqTheme {
                TrackRow(track, onClick = { clicks++ }, menuItems = { close ->
                    QueueMenuItems(onPlayNext = { queued += "next" }, onAddToQueue = { queued += "add" }, close = close)
                })
            }
        }
    }

    @Test
    fun `clicking the row calls onClick only`() {
        setContent()
        composeRule.onNodeWithText(track.title).performClick()
        assertThat(clicks).isEqualTo(1)
        composeRule.onNodeWithText("Play next").assertDoesNotExist()
    }

    @Test
    fun `the overflow menu queues the track and closes`() {
        setContent()

        composeRule.onNodeWithContentDescription("More options for ${track.title}").performClick()
        composeRule.onNodeWithText("Play next").performClick()
        composeRule.onNodeWithContentDescription("More options for ${track.title}").performClick()
        composeRule.onNodeWithText("Add to queue").performClick()

        assertThat(queued).containsExactly("next", "add").inOrder()
        assertThat(clicks).isEqualTo(0)
        composeRule.onNodeWithText("Add to queue").assertDoesNotExist()
    }

    @Test
    fun `row reads as one item with title, artist and duration`() {
        setContent()
        composeRule.onNodeWithText(track.title).assertTextEquals(track.title, "Ada Moșoi", "3:45")
    }

    @Test
    fun `the format badge is read with the row`() {
        composeRule.setContent {
            MediatheqTheme {
                TrackRow(
                    track.copy(
                        audioFormat = AudioFormat("eac3", channels = 6, spatialFormat = SpatialFormat.DolbyAtmos),
                    ),
                    onClick = {},
                )
            }
        }

        composeRule
            .onNodeWithText(track.title)
            .assert(hasContentDescription("Dolby Atmos"))
            .assertTextEquals(track.title, "Ada Moșoi", "3:45")
    }

    @Test
    fun `the badge stays when the artists line is replaced`() {
        composeRule.setContent {
            MediatheqTheme {
                TrackRow(
                    track.copy(audioFormat = AudioFormat("flac", channels = 6)),
                    onClick = {},
                    supportingContent = { Text("Queued") },
                )
            }
        }

        composeRule.onNodeWithText(track.title).assert(hasContentDescription("Surround 5.1"))
        composeRule.onNodeWithText("Queued").assertExists()
    }

    private val swipes = mutableListOf<Boolean>()

    private fun setSwipeContent(style: MediaListStyle = MediaListStyle()) {
        composeRule.setContent {
            MediatheqTheme {
                CompositionLocalProvider(LocalMediaListStyle provides style) {
                    TrackRow(track, onClick = { clicks++ }, onSwipe = { swipes += it })
                }
            }
        }
    }

    @Test
    fun `swiping right and left runs each side once`() {
        setSwipeContent()

        composeRule.onNodeWithText(track.title).performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(track.title).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertThat(swipes).containsExactly(true, false).inOrder()
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `the accessibility actions call the same swipe`() {
        setSwipeContent()

        composeRule.onNode(hasCustomAction("Add to queue")).performCustomAction("Add to queue")
        composeRule.onNode(hasCustomAction("Play next")).performCustomAction("Play next")

        assertThat(swipes).containsExactly(true, false).inOrder()
    }

    @Test
    fun `a disabled side does not swipe and has no accessibility action`() {
        setSwipeContent(MediaListStyle(swipeLeft = null))

        composeRule.onNodeWithText(track.title).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertThat(swipes).isEmpty()
        composeRule.onNode(hasCustomAction("Play next")).assertDoesNotExist()
    }

    private fun hasCustomAction(label: String) =
        SemanticsMatcher("has custom action $label") { node ->
            node.config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
                .any { it.label == label }
        }

    private fun SemanticsNodeInteraction.performCustomAction(label: String) {
        val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        composeRule.runOnIdle { action.action() }
    }
}
