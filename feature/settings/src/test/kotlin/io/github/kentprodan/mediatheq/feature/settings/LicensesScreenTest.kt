package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
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

    private fun show(openId: String? = null) {
        composeRule.setContent {
            MediatheqTheme {
                LicensesScreen(LicensesUiState.Content(listOf(APP_ENTRY), openId), { actions += it }, { closed = true })
            }
        }
    }

    @Test
    fun `tapping a row asks to open its license`() {
        show()

        composeRule.onNodeWithText("mediatheQ").performClick()

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
