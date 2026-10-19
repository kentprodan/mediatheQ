package io.github.kentprodan.mediatheq.feature.album

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

// Plain Application: the stateless screen needs no Hilt graph.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AlbumScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val albumId = MediaId(ServerId("s"), "al1")
    private val tracks =
        listOf(
            1,
            2,
        ).map { Track(MediaId(ServerId("s"), "t$it"), "Track $it", emptyList(), 3.minutes, trackNumber = it) }
    private val content = AlbumUiState.Content(Album(albumId, "Satelit", emptyList()), listOf(Disc(1, tracks)))

    @Test
    fun `play, shuffle, a track tap and back send their actions`() {
        val actions = mutableListOf<AlbumAction>()
        var back = false
        composeRule.setContent {
            MediatheqTheme { AlbumScreen(albumId, content, { actions += it }, onBack = { back = true }) }
        }

        composeRule.onNodeWithText("Play").performClick()
        composeRule.onNodeWithText("Shuffle").performClick()
        // The big header pushes the tracks below the fold of the test screen.
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Track 2"))
        composeRule.onNodeWithText("Track 2").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(actions)
            .containsExactly(AlbumAction.Play, AlbumAction.Shuffle, AlbumAction.PlayTrack(tracks[1].id))
            .inOrder()
        assertThat(back).isTrue()
    }

    @Test
    fun `an empty album disables play and shuffle`() {
        composeRule.setContent {
            MediatheqTheme { AlbumScreen(albumId, content.copy(discs = emptyList()), {}, onBack = {}) }
        }

        composeRule.onNodeWithText("Play").assertIsNotEnabled()
        composeRule.onNodeWithText("Shuffle").assertIsNotEnabled()
    }

    @Test
    fun `the playing track is announced as now playing, the others are not`() {
        composeRule.setContent {
            MediatheqTheme { AlbumScreen(albumId, content.copy(currentTrackId = tracks[0].id), {}, onBack = {}) }
        }

        composeRule.onNodeWithText("Track 1").assert(hasStateDescription("Now playing"))
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Track 2"))
        composeRule.onNodeWithText("Track 2").assert(hasStateDescription("Now playing").not())
    }
}
