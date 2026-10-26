package io.github.kentprodan.mediatheq.feature.library

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
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

        composeRule.onNodeWithText("Songs").assertDoesNotExist()
    }
}
