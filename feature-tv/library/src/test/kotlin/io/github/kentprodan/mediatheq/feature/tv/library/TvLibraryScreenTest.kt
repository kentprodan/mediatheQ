package io.github.kentprodan.mediatheq.feature.tv.library

import android.app.Application
import androidx.activity.ComponentActivity
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
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A 1080p TV; plain Application, the lists get fixed state. A remote, not a finger: D-pad keys.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvLibraryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<TvLibraryAction>()

    private fun items(
        kind: TvLibraryItem.Kind,
        names: List<String>,
    ): TvLibraryContent.Items {
        val items = names.map { TvLibraryItem(MediaId(ServerId("s"), it), kind, it) }
        return TvLibraryContent.Items(items, alphabetIndex(names))
    }

    private val albums = items(TvLibraryItem.Kind.Album, (1..24).map { "Album %02d".format(it) } + "Zebra")
    private val artists = items(TvLibraryItem.Kind.Artist, listOf("Ada", "Boards of Canada"))

    private fun state(tab: TvLibraryTab) =
        when (tab) {
            TvLibraryTab.Albums -> TvLibraryUiState(LibraryFilter.Albums, content = albums)
            TvLibraryTab.Artists -> TvLibraryUiState(LibraryFilter.Artists, content = artists)
            else -> TvLibraryUiState(tab.filter, content = TvLibraryContent.Empty)
        }

    private fun press(key: Key) = composeRule.onRoot().performKeyInput { pressKey(key) }

    private fun focus(text: String) =
        composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.RequestFocus)

    @Test
    fun `switching tabs and back, Down lands on the card focused before`() {
        composeRule.setContent {
            MediatheqTvTheme {
                TvLibraryTabsScreen { tab, cameBack ->
                    TvLibraryList(state(tab), {}, TvLibraryNavigation(), restoreFocus = cameBack)
                }
            }
        }

        focus("Album 03")
        // Focusing a tab shows it.
        focus("Artists")
        composeRule.onNodeWithText("Boards of Canada").assertExists()
        focus("Albums")
        press(Key.DirectionDown)

        composeRule.onNodeWithText("Album 03").assertIsFocused()
    }

    @Test
    fun `the sort menu applies the chosen sort`() {
        composeRule.setContent {
            MediatheqTvTheme { TvLibraryList(state(TvLibraryTab.Albums), { actions += it }, TvLibraryNavigation()) }
        }

        focus("Sort: Name")
        press(Key.DirectionCenter)
        composeRule.onNodeWithText("Name").assertIsFocused()
        // The menu is a dialog, its own window: keys go to it.
        composeRule.onNodeWithText("Recently added").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithText("Recently added").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(actions).containsExactly(TvLibraryAction.SetSort(LibrarySort.RecentlyAdded))
        composeRule.onNodeWithText("Sort by").assertDoesNotExist()
    }

    @Test
    fun `a letter of the strip jumps to its first card and focuses it`() {
        composeRule.setContent {
            MediatheqTvTheme { TvLibraryList(state(TvLibraryTab.Albums), {}, TvLibraryNavigation()) }
        }

        focus("Z")
        press(Key.DirectionCenter)
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText("Zebra").assertIsFocused()
    }

    @Test
    fun `walking the tabs onto an empty one leaves focus on the tabs`() {
        composeRule.setContent {
            MediatheqTvTheme {
                TvLibraryTabsScreen { tab, cameBack ->
                    TvLibraryList(state(tab), {}, TvLibraryNavigation(), restoreFocus = cameBack)
                }
            }
        }

        focus("Artists")
        press(Key.DirectionRight)
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText("Nothing here yet").assertExists()
        composeRule.onNodeWithText("Playlists").assertIsFocused()
    }

    @Test
    fun `Left from the strip returns to the card focused last`() {
        composeRule.setContent {
            MediatheqTvTheme { TvLibraryList(state(TvLibraryTab.Albums), {}, TvLibraryNavigation()) }
        }

        focus("Album 02")
        focus("A")
        press(Key.DirectionLeft)

        composeRule.onNodeWithText("Album 02").assertIsFocused()
    }

    @Test
    fun `a genre page opens with its first card focused`() {
        composeRule.setContent {
            MediatheqTvTheme {
                TvLibraryList(state(TvLibraryTab.Albums), {
                }, TvLibraryNavigation(), restoreFocus = true, title = "Techno", focusFirst = true)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText("Album 01").assertIsFocused()
    }
}
