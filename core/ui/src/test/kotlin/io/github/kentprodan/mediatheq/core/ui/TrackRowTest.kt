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
    private var moreClicks = 0

    private fun setContent() {
        composeRule.setContent {
            MediatheqTheme {
                TrackRow(track, onClick = { clicks++ }, onMoreClick = { moreClicks++ })
            }
        }
    }

    @Test
    fun `clicking the row calls onClick only`() {
        setContent()
        composeRule.onNodeWithText(track.title).performClick()
        assertThat(clicks).isEqualTo(1)
        assertThat(moreClicks).isEqualTo(0)
    }

    @Test
    fun `clicking the overflow button calls onMoreClick only`() {
        setContent()
        composeRule.onNodeWithContentDescription("More options for ${track.title}").performClick()
        assertThat(moreClicks).isEqualTo(1)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `row reads as one item with title, artist and duration`() {
        setContent()
        composeRule.onNodeWithText(track.title).assertTextEquals(track.title, "Ada Moșoi", "3:45")
    }
}
