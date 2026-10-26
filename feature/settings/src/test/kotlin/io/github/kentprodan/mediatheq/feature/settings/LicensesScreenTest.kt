package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LicensesScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<LicensesAction>()
    private var closed = false

    private val withNotice =
        LicenseEntry("jakarta", "Jakarta Inject", "2.0.1", "Apache-2.0", "Apache text", null, notice = "Jakarta notice")

    private fun show(openId: String? = null) {
        composeRule.setContent {
            MediatheqTheme {
                LicensesScreen(
                    LicensesUiState.Content(listOf(APP_ENTRY, withNotice), openId),
                    { actions += it },
                    { closed = true },
                )
            }
        }
    }

    @Test
    fun `an open library with a NOTICE shows it under its license`() {
        show(openId = "jakarta")

        composeRule.onNodeWithText("Notice").assertExists()
        composeRule.onNodeWithText("Jakarta notice").assertExists()
    }

    @Test
    fun `an open row without a NOTICE has no notice heading`() {
        show(openId = "mediatheq")

        composeRule.onNodeWithText("Notice").assertDoesNotExist()
    }

    @Test
    fun `tapping a row asks to open its license, and TalkBack hears whether it is open`() {
        show()

        composeRule
            .onNodeWithText("mediatheQ")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "License hidden"))
            .performClick()

        assertThat(actions).containsExactly(LicensesAction.Toggle("mediatheq"))
    }

    @Test
    fun `an open row shows its license text and a link to its page`() {
        show(openId = "mediatheq")

        composeRule.onNodeWithText("free software", substring = true).assertExists()
        composeRule.onNodeWithText("Open the project page").assertExists()
    }

    @Test
    fun `a closed row hides its text, and back closes the screen`() {
        show()

        composeRule.onNodeWithText("free software", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(closed).isTrue()
    }
}
