package io.github.kentprodan.mediatheq.feature.nowplaying

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class NowPlayingScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val ada = Artist(MediaId(ServerId("s"), "ar1"), "Ada")
    private val bo = Artist(MediaId(ServerId("s"), "ar2"), "Bo")
    private val album = MediaId(ServerId("s"), "al1")
    private val playing =
        NowPlayingUiState.Playing(
            title = "Strobe",
            artist = "Ada",
            artwork = null,
            isPlaying = true,
            duration = 10.minutes,
            isSeekable = true,
            hasNext = true,
            hasPrevious = true,
            artists = listOf(ada),
            albumId = album,
            albumTitle = "Moșoi",
            quality = QualityLabel(QualityKind.Lossless, "FLAC · 16/44.1 · 900 kb/s"),
        )
    private val actions = mutableListOf<NowPlayingAction>()
    private val opened = mutableListOf<String>()

    private fun show(
        state: NowPlayingUiState.Playing = playing,
        lyrics: LyricsUiState = LyricsUiState.None,
    ) = composeRule.setContent {
        MediatheqTheme {
            NowPlayingScreen(
                state,
                position = { 1.minutes },
                onAction = { actions += it },
                onCollapse = { opened += "collapse" },
                lyrics = lyrics,
                navigation =
                    NowPlayingNavigation(
                        onOpenQueue = { opened += "queue" },
                        onOpenArtist = { opened += "artist ${it.itemId}" },
                        onOpenAlbum = { opened += "album ${it.itemId}" },
                        onOpenEqualizer = { opened += "equalizer" },
                        onAddToPlaylist = { opened += "playlist" },
                    ),
            )
        }
    }

    @Test
    fun `the cover button and the bottom bar both toggle the lyrics`() {
        show()

        composeRule.onAllNodesWithContentDescription("Lyrics").fetchSemanticsNodes().also { assertThat(it).hasSize(2) }
        composeRule.onAllNodesWithContentDescription("Lyrics")[0].performClick()
        composeRule.onAllNodesWithContentDescription("Lyrics")[1].performClick()

        assertThat(actions).containsExactly(NowPlayingAction.ToggleLyrics, NowPlayingAction.ToggleLyrics)
    }

    @Test
    fun `a tap on the quality label switches to its detail`() {
        show()

        composeRule.onNodeWithText("Lossless").performClick()

        assertThat(actions).containsExactly(NowPlayingAction.ToggleQualityDetail)
    }

    @Test
    fun `the detail shows when chosen`() {
        show(playing.copy(qualityDetail = true))

        composeRule.onNodeWithText("FLAC · 16/44.1 · 900 kb/s").assertExists()
    }

    @Test
    fun `queue and equalizer open their screens`() {
        show()

        composeRule.onNodeWithContentDescription("Open queue").performClick()
        composeRule.onNodeWithContentDescription("Equalizer").performClick()

        assertThat(opened).containsExactly("queue", "equalizer").inOrder()
    }

    @Test
    fun `one artist opens at once, the album opens its page`() {
        show()

        composeRule.onNode(hasText("Ada") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Moșoi") and hasClickAction()).performClick()

        assertThat(opened).containsExactly("artist ar1", "album al1").inOrder()
    }

    @Test
    fun `several artists open a menu to pick from`() {
        show(playing.copy(artist = "Ada, Bo", artists = listOf(ada, bo)))

        composeRule.onNode(hasText("Ada, Bo") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Bo") and hasClickAction()).performClick()

        assertThat(opened).containsExactly("artist ar2")
    }

    @Test
    fun `a swipe on the cover plays the next or the previous track`() {
        show()

        swipeOnCover(toLeft = true)
        swipeOnCover(toLeft = false)

        assertThat(actions).containsExactly(NowPlayingAction.Next, NowPlayingAction.Previous).inOrder()
    }

    /** Swipes across the upper part of the screen, where the cover is. */
    private fun swipeOnCover(toLeft: Boolean) {
        composeRule.onRoot().performTouchInput {
            val y = height * COVER_HEIGHT_FRACTION
            val (from, to) = if (toLeft) width * 0.8f to width * 0.2f else width * 0.2f to width * 0.8f
            swipe(Offset(from, y), Offset(to, y))
        }
    }

    @Test
    fun `every icon button has a label`() {
        show()

        composeRule
            .onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .forEach { node ->
                val config = node.config
                val named =
                    config.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) ||
                        config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)
                assertThat(named).isTrue()
            }
    }

    @Test
    fun `a pull down over the lyrics at their top closes the player`() {
        // The first line starts after the position: the list rests at its top.
        val lines = Lyrics((0 until 40).map { LyricLine("Line $it", (120 + it * 5).seconds) })
        show(playing.copy(lyricsShown = true), LyricsUiState.Content(lines))

        composeRule.onRoot().performTouchInput {
            val x = width * 0.5f
            swipe(Offset(x, height * 0.15f), Offset(x, height * 0.6f))
        }
        // The fling after the drag runs in the composition's coroutines.
        composeRule.waitForIdle()

        assertThat(opened).contains("collapse")
    }

    private companion object {
        const val COVER_HEIGHT_FRACTION = 0.3f
    }
}
