package io.github.kentprodan.mediatheq.feature.tv.onboarding

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.feature.tv.onboarding.login.TvLoginScreen
import io.github.kentprodan.mediatheq.feature.tv.onboarding.login.TvLoginUiState
import io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect.TvQuickConnectScreen
import io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect.TvQuickConnectUiState
import io.github.kentprodan.mediatheq.feature.tv.onboarding.server.TvServerAction
import io.github.kentprodan.mediatheq.feature.tv.onboarding.server.TvServerScreen
import io.github.kentprodan.mediatheq.feature.tv.onboarding.server.TvServerUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A 1080p TV; plain Application, the screens get fixed state.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvOnboardingScreensTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `quick connect shows the code and the password button`() {
        var password = 0
        composeRule.setContent {
            MediatheqTvTheme {
                TvQuickConnectScreen(TvQuickConnectUiState.Waiting("Home", "123456"), {}, { password++ })
            }
        }

        composeRule.onNodeWithText("123456").assertExists()
        composeRule.onNodeWithText("New code").assertIsFocused()
        // A remote, not a finger: focus moves right, the center key presses.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Use password instead").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertThat(password).isEqualTo(1)
    }

    @Test
    fun `with quick connect off the password button is shown and focused`() {
        composeRule.setContent {
            MediatheqTvTheme { TvQuickConnectScreen(TvQuickConnectUiState.Disabled("Home"), {}, {}) }
        }

        composeRule.onNodeWithText("Use password instead").assertIsFocused()
        composeRule.onNodeWithText("New code").assertDoesNotExist()
    }

    @Test
    fun `the server screen starts in the address field and sends what is typed`() {
        val actions = mutableListOf<TvServerAction>()
        composeRule.setContent { MediatheqTvTheme { TvServerScreen(TvServerUiState(), { actions += it }) } }

        composeRule.onNodeWithText("Server address", useUnmergedTree = true).assertExists()
        composeRule.onNode(hasSetTextAction()).assertIsFocused().performTextInput("nas")

        assertThat(actions).containsExactly(TvServerAction.AddressChanged("nas"))
    }

    @Test
    fun `the password screen starts in the user name`() {
        composeRule.setContent { MediatheqTvTheme { TvLoginScreen(TvLoginUiState("Home"), {}) } }

        composeRule
            .onAllNodes(hasSetTextAction())[0]
            .assertIsFocused()
    }
}
