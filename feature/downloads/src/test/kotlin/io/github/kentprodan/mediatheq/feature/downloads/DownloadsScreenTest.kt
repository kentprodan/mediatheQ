package io.github.kentprodan.mediatheq.feature.downloads

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<DownloadsAction>()
    private val done = Track(MediaId(ServerId("s"), "t1"), "Strobe", emptyList(), 10.minutes, albumTitle = "For Lack")
    private val failed = done.copy(id = MediaId(ServerId("s"), "t2"), title = "Ghosts")

    private fun show(vararg downloads: TrackDownload) {
        val state = DownloadsUiState.Content(listOf(DownloadedAlbum("For Lack", downloads.toList())), 1_000_000)
        composeRule.setContent { MediatheqTheme { DownloadsScreen(state) { actions += it } } }
    }

    @Test
    fun `tapping a track plays it`() {
        show(TrackDownload(done, DownloadState.Completed))

        composeRule.onNodeWithText("Strobe").performClick()

        assertThat(actions).containsExactly(DownloadsAction.Play(done))
    }

    @Test
    fun `remove download in the row menu removes it`() {
        show(TrackDownload(done, DownloadState.Completed))

        composeRule.onNodeWithContentDescription("More options for Strobe").performClick()
        composeRule.onNodeWithText("Remove download").performClick()

        assertThat(actions).containsExactly(DownloadsAction.Remove(done))
    }

    @Test
    fun `a failed download says so and can be downloaded again`() {
        show(TrackDownload(failed, DownloadState.Failed))
        composeRule.onNodeWithText("Download failed").assertExists()

        composeRule.onNodeWithContentDescription("More options for Ghosts").performClick()
        composeRule.onNodeWithText("Download again").performClick()

        assertThat(actions).containsExactly(DownloadsAction.Retry(failed))
    }

    @Test
    fun `a converted download says it is a converted copy`() {
        show(TrackDownload(done, DownloadState.Completed, converted = true))
        composeRule.onNodeWithText("Converted copy").assertExists()
    }

    @Test
    fun `a finished original shows no status`() {
        show(TrackDownload(done, DownloadState.Completed))
        composeRule.onNodeWithText("Converted copy").assertDoesNotExist()
    }

    @Test
    fun `waiting for Wi-Fi is shown on the row`() {
        show(TrackDownload(done, DownloadState.WaitingForWifi))

        composeRule.onNodeWithText("Waiting for Wi-Fi").assertExists()
    }
}
