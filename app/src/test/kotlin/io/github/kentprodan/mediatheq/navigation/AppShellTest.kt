package io.github.kentprodan.mediatheq.navigation

import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Plain Application: the shell needs no Hilt graph.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent() {
        composeRule.setContent {
            MediatheqTheme {
                val navigator = rememberTabNavigator()
                AppShell(navigator, remember(navigator) { placeholderEntries(navigator) })
            }
        }
    }

    @Test
    fun `switching tabs keeps the Library stack`() {
        setContent()
        composeRule.onNodeWithText("Library").performClick()
        composeRule.onNodeWithText("Open detail").performClick()
        composeRule.onNodeWithText("Library 1").assertExists()

        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNodeWithText("Library 1").assertDoesNotExist()

        composeRule.onNodeWithText("Library").performClick()
        composeRule.onNodeWithText("Library 1").assertExists()
    }

    @Test
    fun `re-selecting the current tab returns to its root`() {
        setContent()
        composeRule.onNodeWithText("Library").performClick()
        composeRule.onNodeWithText("Open detail").performClick()

        composeRule.onNodeWithText("Library").performClick()

        composeRule.onNodeWithText("Library 1").assertDoesNotExist()
        composeRule.onNodeWithText("Open detail").assertExists()
    }
}
