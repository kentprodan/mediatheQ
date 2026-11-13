package io.github.kentprodan.mediatheq.feature.nowplaying

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.QualityKind
import io.github.kentprodan.mediatheq.core.common.QualityLabel
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// A Pixel 9 held sideways at the default display and text size (#190).
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w915dp-h412dp-land-xxhdpi")
class NowPlayingLandscapeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val playing =
        NowPlayingUiState.Playing(
            title = "Strobe (Extended Mix, Remastered for the Anniversary Edition)",
            artist = "deadmau5, Kaskade",
            artists =
                listOf(
                    Artist(MediaId(ServerId("s"), "a1"), "deadmau5"),
                    Artist(MediaId(ServerId("s"), "a2"), "Kaskade"),
                ),
            quality = QualityLabel(QualityKind.HiResLossless, "FLAC · 24/96 · 1250 kb/s"),
            albumId = MediaId(ServerId("s"), "al"),
            albumTitle = "For Lack of a Better Name",
            trackNumber = QueuePosition(3, 10),
            artwork = null,
            isPlaying = true,
            duration = 10.minutes,
            isSeekable = true,
            hasNext = true,
            hasPrevious = true,
        )

    @Test
    fun `sideways at the default size everything fits, nothing scrolls, the transport row is on screen`() {
        composeRule.setContent {
            MediatheqTheme {
                // The status bar (taller than the Pixel 9 one, for room to spare) and the gesture bar: Robolectric draws none.
                Box(Modifier.padding(top = STATUS_BAR, bottom = GESTURE_BAR)) {
                    NowPlayingScreen(playing, { Duration.ZERO }, {}, {})
                }
            }
        }

        val scrollable =
            composeRule
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .fetchSemanticsNodes()
        // The right column keeps its scroll for the largest sizes: it must be there, with nothing to scroll.
        assertThat(scrollable).isNotEmpty()
        scrollable.forEach {
            assertThat(it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)!!.maxValue()).isEqualTo(0f)
        }
        val pause = composeRule.onAllNodesWithContentDescription("Pause").onFirst().fetchSemanticsNode()
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val gestureBar = with(composeRule.density) { GESTURE_BAR.toPx() }
        assertThat(pause.boundsInRoot.bottom).isAtMost(screen.bottom - gestureBar)
    }

    private companion object {
        val STATUS_BAR = 40.dp
        val GESTURE_BAR = 24.dp
    }
}
