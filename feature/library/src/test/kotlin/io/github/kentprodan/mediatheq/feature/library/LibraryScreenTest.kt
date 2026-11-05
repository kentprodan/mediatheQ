package io.github.kentprodan.mediatheq.feature.library

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.TopButtonStyle
import io.github.kentprodan.mediatheq.core.data.YearAlbums
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.YearKey
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
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
}
