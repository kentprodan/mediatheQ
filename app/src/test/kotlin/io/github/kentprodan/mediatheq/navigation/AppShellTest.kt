package io.github.kentprodan.mediatheq.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.entryProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.MainNavEntry
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.PortraitNavStyle
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
    private fun Shell() {
        MediatheqTheme {
            val navigator = rememberTabNavigator()
            AppShell(navigator, remember(navigator) { placeholderEntries(navigator) })
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
    fun `a page leaving after back takes no taps`() {
        // #189: the popped page stays on top of the one below until its exit transition ends.
        composeRule.setContent { Shell() }
        openLibraryDetail()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.onNodeWithText("Open next").performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Library 2").assertDoesNotExist()
        composeRule.onNodeWithText("Open detail").assertExists()
    }

    @Test
    fun `a page being opened takes taps before its transition ends`() {
        composeRule.setContent { Shell() }
        composeRule.onNodeWithText("Library").performClick()
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Open detail").performClick()
        composeRule.waitForIdle()
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.onNodeWithText("Open next").performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Library 2").assertExists()
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

    @Composable
    private fun StyledShell(navigation: NavigationPreferences) {
        MediatheqTheme {
            val navigator = rememberTabNavigator(navigation.entries.map(TopLevelTab::of))
            AppShell(navigator, remember(navigator) { placeholderEntries(navigator) }, navigation = navigation)
        }
    }

    private val sixEntries =
        listOf(
            MainNavEntry.Home,
            MainNavEntry.Library,
            MainNavEntry.Albums,
            MainNavEntry.Tracks,
            MainNavEntry.Downloads,
            MainNavEntry.Search,
        )

    @Test
    fun `tapping a floating toolbar entry selects its tab`() {
        composeRule.setContent {
            StyledShell(NavigationPreferences(sixEntries, portraitStyle = PortraitNavStyle.FloatingToolbar))
        }

        composeRule.onNodeWithContentDescription("Tracks").performClick()

        composeRule.onNodeWithContentDescription("Tracks").assertIsSelected()
        composeRule.onNodeWithText("Tracks").assertExists()
    }

    @Test
    fun `with a FAB the last entry is the FAB and selects its tab`() {
        composeRule.setContent {
            StyledShell(NavigationPreferences(sixEntries, portraitStyle = PortraitNavStyle.FloatingToolbarWithFab))
        }

        composeRule.onNodeWithContentDescription("Search").performClick()

        composeRule.onNodeWithContentDescription("Search").assertIsSelected()
        composeRule.onNodeWithText("Open detail").assertExists()
        composeRule.onNodeWithContentDescription("Home").assertIsNotSelected()
    }

    @Test
    fun `only Home hides the navigation`() {
        composeRule.setContent { StyledShell(NavigationPreferences(listOf(MainNavEntry.Home))) }

        composeRule.onNode(hasText("Home") and hasClickAction()).assertDoesNotExist()
        composeRule.onNodeWithText("Open theme showcase").assertExists()
    }

    @Test
    fun `the bottom bar shows the chosen entries in order`() {
        composeRule.setContent {
            StyledShell(NavigationPreferences(listOf(MainNavEntry.Home, MainNavEntry.Playlists, MainNavEntry.Search)))
        }

        val playlistsTab = hasText("Playlists") and hasClickAction()
        composeRule.onNode(playlistsTab).performClick()

        composeRule.onNode(playlistsTab).assertIsSelected()
        composeRule.onNodeWithText("Library").assertDoesNotExist()
    }

    @Test
    fun `labels hide only with many tabs and a large font`() {
        assertThat(showsLabels(tabs = 6, fontScale = 1f)).isTrue()
        assertThat(showsLabels(tabs = 4, fontScale = 2f)).isTrue()
        assertThat(showsLabels(tabs = 5, fontScale = 2f)).isFalse()
    }
}
