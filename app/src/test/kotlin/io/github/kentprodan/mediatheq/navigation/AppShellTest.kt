package io.github.kentprodan.mediatheq.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
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
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun Shell(onLogout: () -> Unit = {}) {
        MediatheqTheme {
            val navigator = rememberTabNavigator()
            AppShell(navigator, remember(navigator) { placeholderEntries(navigator, onLogout) })
        }
    }

    private fun openLibraryDetail() {
        composeRule.onNodeWithText("Library").performClick()
        composeRule.onNodeWithText("Open detail").performClick()
        composeRule.onNodeWithText("Library 1").assertExists()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun `switching tabs keeps the Library stack`() {
        composeRule.setContent { Shell() }
        openLibraryDetail()

        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNodeWithText("Library 1").assertDoesNotExist()

        composeRule.onNodeWithText("Library").performClick()
        composeRule.onNodeWithText("Library 1").assertExists()
    }

    @Test
    fun `re-selecting the current tab returns to its root`() {
        composeRule.setContent { Shell() }
        openLibraryDetail()

        composeRule.onNodeWithText("Library").performClick()

        composeRule.onNodeWithText("Library 1").assertDoesNotExist()
        composeRule.onNodeWithText("Open detail").assertExists()
    }

    @Test
    fun `system back pops the tab, then returns to Home`() {
        composeRule.setContent { Shell() }
        openLibraryDetail()

        pressBack()
        composeRule.onNodeWithText("Library 1").assertDoesNotExist()
        composeRule.onNodeWithText("Open detail").assertExists()

        pressBack()
        composeRule.onNodeWithText("Open theme showcase").assertExists()
    }

    @Test
    fun `stacks and the current tab survive state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { Shell() }
        openLibraryDetail()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Library 1").assertExists()
        composeRule.onNodeWithText("Library").assertIsSelected()
    }

    @Test
    fun `Log out on Home calls the logout action`() {
        var loggedOut = false
        composeRule.setContent { Shell(onLogout = { loggedOut = true }) }

        composeRule.onNodeWithText("Log out").performClick()

        assertThat(loggedOut).isTrue()
    }
}
