package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.google.common.truth.Truth.assertWithMessage
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The focused state of each card variant (#518). Layoutlib draws no focus, so the screenshot shows the variants
 * unfocused and this test proves the focused look: the focusable card grows to the guideline's 1.05.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class MqTvCardFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `every variant scales up when focused`() {
        val requesters = MqTvCardStyle.entries.associateWith { FocusRequester() }
        composeRule.setContent {
            MediatheqTvTheme {
                Row {
                    MqTvCardStyle.entries.forEach { style ->
                        MqTvCard(
                            style.name,
                            {},
                            Modifier.focusRequester(requesters.getValue(style)),
                            "2024",
                            style = style,
                        ) {
                            Box(Modifier.fillMaxSize().testTag(style.name))
                        }
                    }
                }
            }
        }

        // The scale is a layer under the focus node, so the image inside the card shows it.
        fun width(style: MqTvCardStyle) =
            composeRule
                .onNodeWithTag(style.name, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot.width
        val unfocused = MqTvCardStyle.entries.map(::width)

        MqTvCardStyle.entries.forEachIndexed { i, style ->
            composeRule.runOnIdle { requesters.getValue(style).requestFocus() }
            composeRule.onNode(isFocused()).assertExists()
            assertWithMessage(style.name).that(width(style) / unfocused[i]).isWithin(0.01f).of(1.05f)
        }
    }
}
