package io.github.kentprodan.mediatheq.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.entryProvider
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.ui.AlbumCard
import io.github.kentprodan.mediatheq.feature.album.AlbumScreen
import io.github.kentprodan.mediatheq.feature.album.AlbumUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the real shared-cover path (shell scopes present), which screen-level tests and previews skip. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SharedCoverTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val album = Album(MediaId(ServerId("s"), "al1"), "Satelit", emptyList(), artwork = Artwork("al1", "tag"))

    @Test
    fun `opening an album from the grid and going back flies the cover without crashing`() {
        composeRule.setContent {
            MediatheqTheme {
                val navigator = rememberTabNavigator()
                val entries =
                    remember(navigator) {
                        entryProvider {
                            entry<LibraryKey> {
                                AlbumCard(
                                    album,
                                    onClick = { navigator.navigateTo(AlbumKey(album.id)) },
                                )
                            }
                            entry<AlbumKey> {
                                AlbumScreen(
                                    album.id,
                                    AlbumUiState.Content(album, emptyList()),
                                    {},
                                    onBack = { navigator.back() },
                                )
                            }
                            placeholders(navigator, includeLibrary = false)
                        }
                    }
                AppShell(navigator, entries)
            }
        }

        composeRule.onNodeWithText("Library").performClick()
        composeRule.onNodeWithText("Satelit").performClick()
        // The album page is open (its back button; the list may be below the small test screen).
        composeRule.onNodeWithContentDescription("Back").assertExists()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        composeRule.onNodeWithText("Satelit").assertExists()
    }
}
