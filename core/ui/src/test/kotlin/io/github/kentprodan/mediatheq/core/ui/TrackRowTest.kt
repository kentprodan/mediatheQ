package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
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
}
