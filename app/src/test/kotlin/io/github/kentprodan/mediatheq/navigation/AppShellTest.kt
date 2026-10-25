package io.github.kentprodan.mediatheq.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.feature.nowplaying.NowPlayingLayout
import io.github.kentprodan.mediatheq.feature.nowplaying.NowPlayingUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// Plain Application: the shell needs no Hilt graph.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun Shell(offline: Boolean = false) {
        MediatheqTheme {
            val navigator = rememberTabNavigator()
            AppShell(navigator, remember(navigator) { placeholderEntries(navigator) }, offline = offline)
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
    fun `offline the tabs show the offline state, and its button opens Downloads, which works`() {
        composeRule.setContent { Shell(offline = true) }
        composeRule.onNodeWithText("You are offline").assertExists()

        composeRule.onNodeWithText("Open Downloads").performClick()

        composeRule.onNodeWithText("You are offline").assertDoesNotExist()
        composeRule.onNode(hasText("Downloads") and hasClickAction()).assertIsSelected()
    }

    @Test
    fun `offline, back still pops the tab`() {
        var offline by mutableStateOf(false)
        composeRule.setContent { Shell(offline = offline) }
        openLibraryDetail()
        offline = true
        composeRule.waitForIdle()

        pressBack()
        offline = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Library 1").assertDoesNotExist()
        composeRule.onNodeWithText("Open detail").assertExists()
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
    fun `back closes the open player first and leaves the tab stack alone`() {
        val playing =
            NowPlayingUiState.Playing("Strobe", "deadmau5", null, true, 10.minutes, true, true, true)
        composeRule.setContent {
            MediatheqTheme {
                val navigator = rememberTabNavigator()
                val entries = remember(navigator) { placeholderEntries(navigator) }
                NowPlayingLayout(playing, MutableStateFlow(Duration.ZERO), {}, content = { mini ->
                    AppShell(navigator, entries, miniPlayer = mini)
                })
            }
        }
        openLibraryDetail()
        composeRule.onNode(hasText("Strobe") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Close player").assertExists()

        pressBack()

        composeRule.onNodeWithContentDescription("Close player").assertDoesNotExist()
        composeRule.onNodeWithText("Library 1").assertExists()
    }
}
