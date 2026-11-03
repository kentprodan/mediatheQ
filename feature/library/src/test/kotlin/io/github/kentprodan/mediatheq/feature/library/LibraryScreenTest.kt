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
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.TopButtonStyle
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
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
    fun `tapping an album opens it and a filter selects its tab`() {
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
        composeRule.onNodeWithText("Songs").performClick()

        assertThat(opened).isEqualTo(albums[1].id)
        assertThat(actions).contains(LibraryAction.SelectFilter(LibraryFilter.Songs))
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
    fun `R52 no filter bar until the saved style is known`() {
        composeRule.setContent {
            MediatheqTheme {
                LibraryScreen(
                    LibraryUiState(filterStyle = null),
                    onAction = {},
                    onOpenAlbum = {},
                    onOpenArtist = {},
                    onOpenPlaylist = {},
                )
            }
        }

        composeRule.onNodeWithText("Library").assertExists()
        composeRule.onNodeWithText("Songs").assertDoesNotExist()
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
