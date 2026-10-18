package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MqLoadingIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `indicator appears only after the delay`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { MediatheqTheme { MqLoadingIndicator(delayMillis = 300) } }

        composeRule.mainClock.advanceTimeBy(250)
        assertThat(composeRule.onAllNodesWithContentDescription("Loading").fetchSemanticsNodes()).isEmpty()

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithContentDescription("Loading").assertExists()
    }
}
