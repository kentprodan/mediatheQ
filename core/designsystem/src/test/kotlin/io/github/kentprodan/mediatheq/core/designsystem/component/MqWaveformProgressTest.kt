package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MqWaveformProgressTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val seeks = mutableListOf<Float>()

    private fun show(
        levels: List<Float>? = List(200) { 0.5f },
        fallback: MqProgressFallback = MqProgressFallback.Basic,
        enabled: Boolean = true,
    ) = composeRule.setContent {
        MediatheqTheme {
            MqWaveformProgress(
                levels,
                0.25f,
                "1:00 of 4:00",
                onSeek = { seeks += it },
                enabled = enabled,
                fallback = fallback,
            )
        }
    }

    private fun bar() = composeRule.onNodeWithContentDescription("Playback position")

    @Test
    fun `a tap seeks to where it lands`() {
        show()

        bar().performTouchInput { click(Offset(width * 0.75f, centerY)) }

        assertThat(seeks).hasSize(1)
        assertThat(seeks.single()).isWithin(0.02f).of(0.75f)
    }

    @Test
    fun `a drag seeks once, where it is let go`() {
        show()

        bar().performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.4f, centerY))
            moveTo(Offset(width * 0.6f, centerY))
            up()
        }

        assertThat(seeks).hasSize(1)
        assertThat(seeks.single()).isWithin(0.02f).of(0.6f)
    }

    @Test
    fun `TalkBack reads a slider with the position and can set it`() {
        show()

        bar()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(
                        0.25f,
                        0f..1f,
                    ),
                ),
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1:00 of 4:00"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }

        assertThat(seeks).containsExactly(0.5f)
    }

    @Test
    fun `the wavy fallback seeks too and is one slider for TalkBack`() {
        show(levels = null, fallback = MqProgressFallback.Wavy)

        bar().performTouchInput { click(Offset(width * 0.5f, centerY)) }

        assertThat(seeks.single()).isWithin(0.02f).of(0.5f)
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .fetchSemanticsNodes()
            .also { assertThat(it).hasSize(1) }
    }

    @Test
    fun `a stream that cannot be seeked ignores taps`() {
        show(enabled = false)

        bar().performTouchInput { click(Offset(width * 0.5f, centerY)) }

        assertThat(seeks).isEmpty()
    }
}
