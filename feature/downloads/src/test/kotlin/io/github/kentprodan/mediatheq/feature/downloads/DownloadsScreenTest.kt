package io.github.kentprodan.mediatheq.feature.downloads

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AutoDownloads
import io.github.kentprodan.mediatheq.core.data.CleanupPreview
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class DownloadsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val actions = mutableListOf<DownloadsAction>()
    private val opened = mutableListOf<String>()
    private val done = Track(id("t1"), "Strobe", emptyList(), 10.minutes, albumTitle = "For Lack")
    private val failed = done.copy(id = id("t2"), title = "Ghosts")
    private val album = Album(id("a1"), "For Lack of a Better Name", emptyList())
    private val playlist = Playlist(id("p1"), "Night drive")

    private fun show(
        downloading: List<TrackDownload> = emptyList(),
        songs: List<TrackDownload> = emptyList(),
        autoAlbum: Boolean = false,
        tab: DownloadsTab = DownloadsTab.Files,
        paused: Boolean = false,
        rules: AutoRules = AutoRules(),
        cleanup: CleanupPreview? = null,
    ) {
        val state =
            DownloadsUiState.Content(
                DownloadTotals(1_000_000, 20.minutes, 9_000_000),
                downloading,
                listOf(DownloadedAlbum(album, listOf(done), 500_000, 10.minutes, auto = autoAlbum)),
                listOf(DownloadedPlaylist(playlist, listOf(done), 500_000, 10.minutes)),
                songs,
                paused = paused,
                rules = rules,
                hasFiles = true,
                cleanup = cleanup,
            )
        composeRule.setContent {
            MediatheqTheme {
                var current by remember { mutableStateOf(tab) }
                DownloadsScreen(
                    state,
                    { actions += it },
                    tab = current,
                    onTab = { current = it },
                    onOpenAlbum = { opened += "album ${it.itemId}" },
                    onOpenPlaylist = { opened += "playlist ${it.itemId}" },
                    onOpenRules = { opened += "rules" },
                )
            }
        }
    }

    private fun scrollTo(text: String) =
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))

    @Test
    fun `sections show only with rows, with their titles as headings`() {
        show()

        composeRule.onNode(isHeading() and hasText("Albums")).assertExists()
        composeRule.onNode(isHeading() and hasText("Playlists")).assertExists()
        composeRule.onNode(isHeading() and hasText("Downloading")).assertDoesNotExist()
        composeRule.onNode(isHeading() and hasText("Songs")).assertDoesNotExist()
    }

    @Test
    fun `tapping an album or a playlist opens it`() {
        show()

        composeRule.onNodeWithText("For Lack of a Better Name").performClick()
        composeRule.onNodeWithText("Night drive").performClick()

        assertThat(opened).containsExactly("album a1", "playlist p1").inOrder()
    }

    @Test
    fun `a collection's menu plays, shuffles and removes it`() {
        show()
        val menu = "More options for For Lack of a Better Name"

        composeRule.onNodeWithContentDescription(menu).performClick()
        composeRule.onNodeWithText("Play").performClick()
        composeRule.onNodeWithContentDescription(menu).performClick()
        composeRule.onNodeWithText("Shuffle").performClick()
        composeRule.onNodeWithContentDescription(menu).performClick()
        composeRule.onNodeWithText("Remove download").performClick()

        assertThat(actions)
            .containsExactly(
                DownloadsAction.PlayCollection(listOf(done), shuffle = false),
                DownloadsAction.PlayCollection(listOf(done), shuffle = true),
                DownloadsAction.RemoveCollection(DownloadOrigin.Album(album.id)),
            ).inOrder()
    }

    @Test
    fun `a favorite's automatic download says Auto and has no Remove, it goes by unfavoriting`() {
        show(
            songs = listOf(TrackDownload(done, DownloadState.Completed, origins = setOf(DownloadOrigin.Favorite))),
            autoAlbum = true,
        )
        composeRule.onAllNodesWithText("Auto ·", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Auto").assertCountEquals(1)

        composeRule.onNodeWithContentDescription("More options for For Lack of a Better Name").performClick()
        composeRule.onNodeWithText("Remove download").assertDoesNotExist()
    }

    @Test
    fun `a song plays on tap and is removed from its menu`() {
        show(songs = listOf(TrackDownload(done, DownloadState.Completed)))
        scrollTo("Strobe")

        composeRule.onNodeWithText("Strobe").performClick()
        composeRule.onNodeWithContentDescription("More options for Strobe").performClick()
        composeRule.onNodeWithText("Remove download").performClick()

        assertThat(actions).containsExactly(DownloadsAction.Play(done), DownloadsAction.RemoveSong(done)).inOrder()
    }

    @Test
    fun `a failed download says so and can be downloaded again`() {
        show(downloading = listOf(TrackDownload(failed, DownloadState.Failed)), tab = DownloadsTab.Queue)
        composeRule.onNodeWithText("Download failed").assertExists()

        composeRule.onNodeWithContentDescription("More options for Ghosts").performClick()
        composeRule.onNodeWithText("Download again").performClick()

        assertThat(actions).containsExactly(DownloadsAction.Retry(failed))
    }

    @Test
    fun `the tabs switch between offline files, the queue and the rules`() {
        show(downloading = listOf(TrackDownload(done, DownloadState.WaitingForWifi)))
        composeRule.onNodeWithText("Waiting for Wi-Fi").assertDoesNotExist()

        composeRule.onNodeWithText("Queue").performClick()
        composeRule.onNodeWithText("Waiting for Wi-Fi").assertExists()

        composeRule.onNodeWithText("Rules").performClick()
        composeRule.onNodeWithText("No automatic downloads").assertExists()
        composeRule.onNodeWithText("Change rules").performClick()
        assertThat(opened).containsExactly("rules")
    }

    @Test
    fun `a waiting download can be cancelled, and pause all holds the queue`() {
        show(downloading = listOf(TrackDownload(done, DownloadState.WaitingForWifi)), tab = DownloadsTab.Queue)

        composeRule.onNodeWithContentDescription("More options for Strobe").performClick()
        composeRule.onNodeWithText("Remove download").performClick()
        composeRule.onNodeWithText("Pause all").performClick()

        assertThat(actions).containsExactly(DownloadsAction.Remove(done), DownloadsAction.PauseAll).inOrder()
    }

    @Test
    fun `paused, the queue says so and offers resume`() {
        show(
            downloading = listOf(TrackDownload(done, DownloadState.WaitingForWifi)),
            tab = DownloadsTab.Queue,
            paused = true,
        )

        composeRule.onNodeWithText("Paused").assertExists()
        composeRule.onNodeWithText("Resume all").performClick()

        assertThat(actions).containsExactly(DownloadsAction.ResumeAll)
    }

    @Test
    fun `cleanup asks first and says what goes`() {
        show(cleanup = CleanupPreview(2, 0))

        composeRule.onNodeWithText("Clean up offline files?").assertExists()
        composeRule.onNodeWithText("2 failed downloads", substring = true).assertExists()
        composeRule.onNodeWithText("Clean up").performClick()

        assertThat(actions).containsExactly(DownloadsAction.ConfirmCleanup)
    }

    @Test
    fun `the summary's Cleanup opens the confirmation`() {
        show()

        composeRule.onNodeWithText("Cleanup").performClick()

        assertThat(actions).containsExactly(DownloadsAction.AskCleanup)
    }

    @Test
    fun `the rules tab shows the automatic downloads setting`() {
        show(tab = DownloadsTab.Rules, rules = AutoRules(AutoDownloads.TracksAndAlbums, removeUnfavorited = false))

        composeRule.onNodeWithText("Favorite tracks and albums").assertExists()
        composeRule.onNodeWithText("Off").assertExists()
    }

    @Test
    fun `a converted song says it is a converted copy`() {
        show(songs = listOf(TrackDownload(done, DownloadState.Completed, converted = true)))
        scrollTo("Converted copy")

        composeRule.onNodeWithText("Converted copy").assertExists()
    }

    @Test
    fun `a playlist gone from the library still has a name`() {
        val state =
            DownloadsUiState.Content(
                DownloadTotals(0, 0.minutes, 0),
                playlists = listOf(DownloadedPlaylist(Playlist(id("gone"), ""), listOf(done), 1, 1.minutes)),
                hasFiles = true,
            )
        composeRule.setContent { MediatheqTheme { DownloadsScreen(state, { actions += it }) } }

        composeRule.onNodeWithText("Playlist").assertExists()
    }

    @Test
    fun `typing in the filter keeps every letter in order, while the list answers a frame late`() {
        composeRule.setContent {
            // The ViewModel's state comes back a frame after each change (#186).
            var filter by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            MediatheqTheme {
                DownloadsScreen(
                    DownloadsUiState.Content(
                        DownloadTotals(1_000_000, 20.minutes, 9_000_000),
                        emptyList(),
                        listOf(DownloadedAlbum(album, listOf(done), 500_000, 10.minutes)),
                        emptyList(),
                        emptyList(),
                        filter = filter,
                        hasFiles = true,
                    ),
                    { action ->
                        actions += action
                        if (action is DownloadsAction.Filter) {
                            scope.launch {
                                withFrameNanos {}
                                filter = action.text
                            }
                        }
                    },
                )
            }
        }

        val field = composeRule.onNode(hasSetTextAction())
        listOf("a", "b", "c").forEach {
            field.performTextInput(it)
            composeRule.waitForIdle()
        }

        field.assertTextEquals("Filter offline files", "abc", includeEditableText = true)
        assertThat(actions.last()).isEqualTo(DownloadsAction.Filter("abc"))
    }
}
