package io.github.kentprodan.mediatheq.feature.downloads

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
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
    ) {
        val state =
            DownloadsUiState.Content(
                DownloadTotals(1_000_000, 20.minutes, 9_000_000),
                downloading,
                listOf(DownloadedAlbum(album, listOf(done), 500_000, 10.minutes)),
                listOf(DownloadedPlaylist(playlist, listOf(done), 500_000, 10.minutes)),
                songs,
            )
        composeRule.setContent {
            MediatheqTheme {
                DownloadsScreen(
                    state,
                    { actions += it },
                    onOpenAlbum = { opened += "album ${it.itemId}" },
                    onOpenPlaylist = { opened += "playlist ${it.itemId}" },
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
    fun `a song plays on tap and is removed from its menu`() {
        show(songs = listOf(TrackDownload(done, DownloadState.Completed)))
        scrollTo("Strobe")

        composeRule.onNodeWithText("Strobe").performClick()
        composeRule.onNodeWithContentDescription("More options for Strobe").performClick()
        composeRule.onNodeWithText("Remove download").performClick()

        assertThat(actions).containsExactly(DownloadsAction.Play(done), DownloadsAction.Remove(done)).inOrder()
    }

    @Test
    fun `a failed download says so and can be downloaded again`() {
        show(downloading = listOf(TrackDownload(failed, DownloadState.Failed)))
        composeRule.onNodeWithText("Download failed").assertExists()

        composeRule.onNodeWithContentDescription("More options for Ghosts").performClick()
        composeRule.onNodeWithText("Download again").performClick()

        assertThat(actions).containsExactly(DownloadsAction.Retry(failed))
    }

    @Test
    fun `waiting for Wi-Fi is shown in the downloading section`() {
        show(downloading = listOf(TrackDownload(done, DownloadState.WaitingForWifi)))

        composeRule.onNode(isHeading() and hasText("Downloading")).assertExists()
        composeRule.onNodeWithText("Waiting for Wi-Fi").assertExists()
    }

    @Test
    fun `a converted song says it is a converted copy`() {
        show(songs = listOf(TrackDownload(done, DownloadState.Completed, converted = true)))
        scrollTo("Converted copy")

        composeRule.onNodeWithText("Converted copy").assertExists()
    }
}
