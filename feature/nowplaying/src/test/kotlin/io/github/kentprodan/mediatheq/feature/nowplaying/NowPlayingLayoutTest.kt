package io.github.kentprodan.mediatheq.feature.nowplaying

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Plain Application: the layout is driven by fixed state, no Hilt graph. Pixel 9 sized window.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class NowPlayingLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val playing =
        NowPlayingUiState.Playing(
            title = "Strobe",
            artist = "deadmau5",
            artwork = null,
            isPlaying = true,
            duration = 10.minutes,
            isSeekable = true,
            hasNext = true,
            hasPrevious = true,
        )
    private val actions = mutableListOf<NowPlayingAction>()
    private var state by mutableStateOf<NowPlayingUiState>(playing)

    private val miniPlayer = hasText("Strobe") and hasClickAction()
    private val closeButton = "Close player"

    private fun setContent() {
        composeRule.setContent {
            MediatheqTheme {
                NowPlayingLayout(state, flowOf(150.seconds), { actions += it }, content = { mini -> Box { mini() } })
            }
        }
    }

    @Test
    fun `tapping the mini player opens the full player with motion, and back closes it`() {
        setContent()
        composeRule.onNode(miniPlayer).assertIsDisplayed()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNode(miniPlayer).performClick()
        // Lets the click land (Robolectric's looper) without moving the frame clock.
        composeRule.waitForIdle()
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        // Mid-transition: the full player is on its way in while the mini player is still leaving.
        composeRule.onNodeWithContentDescription(closeButton).assertExists()
        composeRule.onNode(miniPlayer).assertExists()

        // Frame by frame, so every animation step runs (springs have no fixed length).
        repeat(SETTLE_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.onNodeWithContentDescription(closeButton).assertIsDisplayed()
        composeRule.onNode(miniPlayer).assertDoesNotExist()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        repeat(SETTLE_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.onNodeWithContentDescription(closeButton).assertDoesNotExist()
        composeRule.onNode(miniPlayer).assertIsDisplayed()
    }

    @Test
    fun `the full player sends transport actions and closes with its button`() {
        setContent()
        composeRule.onNode(miniPlayer).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.onNodeWithContentDescription("Next track").performClick()
        composeRule.onNodeWithContentDescription("Previous track").performClick()
        composeRule.onNodeWithContentDescription(closeButton).performClick()
        composeRule.waitForIdle()

        assertThat(actions)
            .containsExactly(NowPlayingAction.TogglePlayPause, NowPlayingAction.Next, NowPlayingAction.Previous)
            .inOrder()
        composeRule.onNode(miniPlayer).assertIsDisplayed()
    }

    @Test
    fun `the mini player plays, pauses and skips without opening`() {
        setContent()

        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.onNodeWithContentDescription("Next track").performClick()

        assertThat(actions).containsExactly(NowPlayingAction.TogglePlayPause, NowPlayingAction.Next).inOrder()
        composeRule.onNodeWithContentDescription(closeButton).assertDoesNotExist()
    }

    @Test
    fun `an emptied queue hides the mini player, and a new one brings it back`() {
        setContent()

        state = NowPlayingUiState.Hidden
        composeRule.waitForIdle()
        composeRule.onNode(miniPlayer).assertDoesNotExist()

        state = playing
        composeRule.waitForIdle()
        composeRule.onNode(miniPlayer).assertIsDisplayed()
    }

    private companion object {
        const val SETTLE_FRAMES = 180
    }
}
