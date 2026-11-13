package io.github.kentprodan.mediatheq.feature.library

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.TopButtonStyle
import io.github.kentprodan.mediatheq.core.data.YearAlbums
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RecordLabel
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.RecordLabelKey
import io.github.kentprodan.mediatheq.core.navigation.YearKey
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LibraryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val albums = List(30) { Album(MediaId(ServerId("s"), "a$it"), "Album $it", emptyList()) }

    @Test
    fun `the album grid survives saving and restoring its state`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MediatheqTheme {
                LibraryScreen(LibraryUiState(content = LibraryContent.Albums(albums)), {}, {}, {}, {})
            }
        }

        // Lazy keys are saved in a Bundle; a non-saveable key type would throw here.
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Album 0").assertExists()
    }

    @Test
    fun `tapping an album opens it`() {
        var opened: MediaId? = null
        val actions = mutableListOf<LibraryAction>()
        composeRule.setContent {
            MediatheqTheme {
                LibraryScreen(
                    LibraryUiState(content = LibraryContent.Albums(albums)),
                    onAction = { actions += it },
                    onOpenAlbum = { opened = it },
                    onOpenArtist = {},
                    onOpenPlaylist = {},
                )
            }
        }

        composeRule.onNodeWithText("Album 1").performClick()

        assertThat(opened).isEqualTo(albums[1].id)
        assertThat(actions).isEmpty()
    }

    @Test
    fun `nothing downloaded opens the Downloads tab`() {
        var downloadsOpened = false
        composeRule.setContent {
            MediatheqTheme {
                LibraryScreen(
                    LibraryUiState(content = LibraryContent.NothingDownloaded),
                    onAction = {},
                    onOpenAlbum = {},
                    onOpenArtist = {},
                    onOpenPlaylist = {},
                    onOpenDownloads = { downloadsOpened = true },
                )
            }
        }

        composeRule.onNodeWithText("Open Downloads").performClick()

        assertThat(downloadsOpened).isTrue()
    }

    @Test
    fun `Back shows on a list opened from the hub, unless hidden in the settings`() {
        var back = 0
        var state by mutableStateOf(LibraryUiState(content = LibraryContent.Albums(albums)))
        composeRule.setContent {
            MediatheqTheme { LibraryScreen(state, {}, {}, {}, {}, onBack = { back++ }) }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertThat(back).isEqualTo(1)

        state = state.copy(header = MediaListPreferences(hideBackButton = true))
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun `a list that is a navigation entry has no Back`() {
        composeRule.setContent {
            MediatheqTheme { LibraryScreen(LibraryUiState(content = LibraryContent.Albums(albums)), {}, {}, {}, {}) }
        }

        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun `a year opens its albums, the unknown year too`() {
        val opened = mutableListOf<YearKey>()
        val years =
            listOf(YearAlbums(1994, albums.take(2)), YearAlbums(null, albums.take(1)))
        composeRule.setContent {
            MediatheqTheme {
                LibraryScreen(
                    LibraryUiState(filter = LibraryFilter.Years, content = LibraryContent.Years(years)),
                    {},
                    {},
                    {},
                    {},
                    onOpenYear = { opened += it },
                )
            }
        }

        composeRule.onNodeWithText("2 albums").assertExists()
        composeRule.onNodeWithText("1994").performClick()
        composeRule.onNodeWithText("Unknown year").performClick()

        assertThat(opened).containsExactly(YearKey(1994), YearKey(null)).inOrder()
        // Years have nothing to select or search.
        composeRule.onNodeWithContentDescription("Search in list").assertDoesNotExist()
    }

    @Test
    fun `a record label opens its page`() {
        val opened = mutableListOf<RecordLabelKey>()
        val label = RecordLabel(MediaId(ServerId("s"), "l1"), "Electrecord", albumCount = 2)
        composeRule.setContent {
            MediatheqTheme {
                LibraryScreen(
                    LibraryUiState(
                        filter = LibraryFilter.RecordLabels,
                        content = LibraryContent.RecordLabels(listOf(label)),
                    ),
                    {},
                    {},
                    {},
                    {},
                    onOpenLabel = { opened += it },
                )
            }
        }

        composeRule.onNodeWithText("2 albums").assertExists()
        composeRule.onNodeWithText("Electrecord").performClick()

        assertThat(opened).containsExactly(RecordLabelKey(label.id, "Electrecord"))
        composeRule.onNodeWithContentDescription("Search in list").assertDoesNotExist()
    }

    @Test
    fun `a hub tile opens its list`() {
        val actions = mutableListOf<LibraryHubAction>()
        composeRule.setContent {
            MediatheqTheme {
                LibraryHubScreen(LibraryHubUiState.Content(LibraryPagePreferences()), { actions += it })
            }
        }

        composeRule.onNodeWithText("Years").performClick()
        composeRule.onNodeWithText("Album artists").performClick()

        assertThat(actions)
            .containsExactly(
                LibraryHubAction.Open(LibraryShortcut.Years),
                LibraryHubAction.Open(LibraryShortcut.AlbumArtists),
            ).inOrder()
    }

    @Test
    fun `a long press starts selecting, a tap then toggles, and the toolbar acts on the selection`() {
        val actions = mutableListOf<LibraryAction>()
        var opened: MediaId? = null
        // The header's own top buttons hidden: they share the toolbar's names.
        var state by mutableStateOf(
            LibraryUiState(
                content = LibraryContent.Albums(albums),
                header = MediaListPreferences(topButtonStyle = TopButtonStyle.Hidden),
            ),
        )
        composeRule.setContent {
            MediatheqTheme {
                LibraryScreen(
                    state,
                    onAction = {
                        actions += it
                        if (it is LibraryAction.ToggleSelection) {
                            state =
                                state.copy(selection = state.selection.toggle(it.id))
                        }
                    },
                    onOpenAlbum = { opened = it },
                    onOpenArtist = {},
                    onOpenPlaylist = {},
                )
            }
        }

        composeRule.onNodeWithText("Album 1").performTouchInput { longClick() }
        composeRule.onNodeWithText("Album 2").performClick()
        composeRule.onNodeWithText("2 selected").assertExists()
        composeRule.onNodeWithContentDescription("Add to queue").performClick()

        assertThat(opened).isNull()
        assertThat(actions)
            .containsAtLeast(
                LibraryAction.ToggleSelection(albums[1].id),
                LibraryAction.ToggleSelection(albums[2].id),
                LibraryAction.OnSelection(SelectionCommand.AddToQueue),
            ).inOrder()
    }

    @Test
    fun `typing in search in list keeps every letter in order, while the list answers a frame late`() {
        val actions = mutableListOf<LibraryAction>()
        composeRule.setContent {
            // The ViewModel's state comes back a frame after each change (#186).
            var search by remember { mutableStateOf<String?>("") }
            val scope = rememberCoroutineScope()
            MediatheqTheme {
                LibraryScreen(
                    LibraryUiState(
                        content = LibraryContent.Albums(albums),
                        listFilter = LibraryListFilter(search = search),
                    ),
                    onAction = { action ->
                        actions += action
                        if (action is LibraryAction.Search) {
                            scope.launch {
                                withFrameNanos {}
                                search = action.text
                            }
                        }
                    },
                    {},
                    {},
                    {},
                )
            }
        }

        val field = composeRule.onNode(hasSetTextAction())
        listOf("a", "b", "c").forEach {
            field.performTextInput(it)
            composeRule.waitForIdle()
        }

        field.assertTextEquals("abc", includeEditableText = true)
        assertThat(actions.last()).isEqualTo(LibraryAction.Search("abc"))
    }

    @Test
    fun `the arrow and the chosen sort in the menu reverse the sort, and both say the direction`() {
        val actions = mutableListOf<LibraryAction>()
        composeRule.setContent {
            MediatheqTheme {
                LibraryScreen(
                    LibraryUiState(content = LibraryContent.Albums(albums), sortReversed = true),
                    onAction = { actions += it },
                    {},
                    {},
                    {},
                )
            }
        }

        val arrow = composeRule.onNodeWithContentDescription("Reverse sort")
        arrow.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Descending"))
        composeRule
            .onNode(hasText("Name") and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Descending"))
        arrow.performClick()
        composeRule.onNode(hasText("Name") and hasClickAction()).performClick()
        composeRule.onAllNodesWithText("Name").onLast().performClick()
        composeRule.onNode(hasText("Name") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Year").performClick()

        assertThat(actions)
            .containsExactly(
                LibraryAction.ReverseSort,
                LibraryAction.ReverseSort,
                LibraryAction.SelectSort(LibrarySort.Year),
            ).inOrder()
    }
}
