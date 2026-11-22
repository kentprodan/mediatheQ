package io.github.kentprodan.mediatheq.feature.tv.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A 1080p TV; the tabs get fixed grids. A remote, not a finger: D-pad keys.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvVideoTabsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val posters = (1..12).map { TvPoster(MediaId(ServerId("s"), "m$it"), "Movie %02d".format(it)) }
    private val grid =
        TvVideoGridUiState.Content(
            posters,
            emptyList(),
            TvVideoSort.DateAdded,
            TvVideoFilter(),
            emptyList(),
            emptyList(),
            12,
        )
    private val genres = listOf(TvVideoGroup("Crime", 2), TvVideoGroup("Drama", 5))
    private val opened = mutableListOf<String>()

    private fun show(kind: TvVideoKind = TvVideoKind.Movies) =
        composeRule.setContent {
            MediatheqTvTheme {
                TvVideoTabsRoute(kind, TvVideoNavigation()) { tab, cameBack -> Tab(tab, cameBack) }
            }
        }

    @Composable
    private fun Tab(
        tab: TvVideoTab,
        cameBack: Boolean,
    ) = when (tab) {
        TvVideoTab.Genres -> {
            TvVideoGroups(TvVideoKind.Movies, TvVideoGroupsUiState.Groups(genres), false, cameBack) { opened += it }
        }

        else -> {
            TvVideoGridScreen(TvVideoKind.Movies, grid, {}, {}, restoreFocus = cameBack)
        }
    }

    private fun press(key: Key) = composeRule.onRoot().performKeyInput { pressKey(key) }

    private fun card(title: String) = composeRule.onNodeWithText(title)

    @Test
    fun `Movies has All, Collections, Genres, Favorites and Studios`() {
        show()
        listOf("All", "Collections", "Genres", "Favorites", "Studios").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
    }

    @Test
    fun `Series has Networks and no Collections`() {
        show(TvVideoKind.Series)
        composeRule.onNodeWithText("Networks").assertExists()
        composeRule.onNodeWithText("Collections").assertDoesNotExist()
    }

    @Test
    fun `Up from the last poster of a row lands on the selected tab, and each tab keeps its focused card`() {
        show()
        card("Movie 06").performSemanticsAction(SemanticsActions.RequestFocus)

        // Up through Sort and Filter onto the tabs: the selected one, the shown tab stays.
        repeat(3) { press(Key.DirectionUp) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("All").assertIsFocused()
        card("Movie 06").assertExists()

        // Over to Genres, onto Drama, back to All: Down lands on the poster focused before.
        repeat(2) { press(Key.DirectionRight) }
        composeRule.mainClock.advanceTimeBy(1_000)
        card("Drama").performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(3) { press(Key.DirectionUp) }
        repeat(2) { press(Key.DirectionLeft) }
        composeRule.mainClock.advanceTimeBy(1_000)
        // Sort and Filter sit on the right, out of the way: Down from the tab goes into the grid.
        press(Key.DirectionDown)

        card("Movie 06").assertIsFocused()
    }

    @Test
    fun `a genre card opens that genre`() {
        show()
        composeRule.onNodeWithText("All").performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(2) { press(Key.DirectionRight) }
        composeRule.mainClock.advanceTimeBy(1_000)

        card("Drama").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)

        assertThat(opened).containsExactly("Drama")
    }

    @Test
    fun `Back in a tab goes up to the tabs, the next Back is left to the shell`() {
        var shellBacks = 0
        composeRule.setContent {
            // The shell's own Back sits under the screen's.
            BackHandler { shellBacks++ }
            MediatheqTvTheme {
                TvVideoTabsRoute(TvVideoKind.Movies, TvVideoNavigation()) { tab, cameBack -> Tab(tab, cameBack) }
            }
        }
        card("Movie 03").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        assertThat(shellBacks).isEqualTo(0)
        composeRule.onNodeWithText("All").assertIsFocused()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        assertThat(shellBacks).isEqualTo(1)
    }

    @Test
    fun `empty Favorites says how to add one, with no Sync now`() {
        composeRule.setContent {
            MediatheqTvTheme {
                TvVideoGridScreen(TvVideoKind.Movies, TvVideoGridUiState.Empty, {}, {}, only = TvVideoOnly.Favorites)
            }
        }

        composeRule.onNodeWithText("No favorites yet").assertExists()
        composeRule.onNodeWithText("Sync now").assertDoesNotExist()
    }

    @Test
    fun `an empty Genres or Studios tab says why, a failed read shows the error`() {
        var state: TvVideoGroupsUiState by mutableStateOf(TvVideoGroupsUiState.Groups(emptyList()))
        var studios by mutableStateOf(false)
        composeRule.setContent {
            MediatheqTvTheme { TvVideoGroups(TvVideoKind.Movies, state, studios, restoreFocus = false) {} }
        }

        composeRule.onNodeWithText("None of the library has a genre yet.").assertExists()
        studios = true
        composeRule.onNodeWithText("Studios come with the next library sync.").assertExists()
        state = TvVideoGroupsUiState.Error
        composeRule.onNodeWithText("Couldn", substring = true).assertExists()
    }
}
