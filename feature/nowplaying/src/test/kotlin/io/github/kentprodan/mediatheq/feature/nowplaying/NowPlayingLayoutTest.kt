package io.github.kentprodan.mediatheq.feature.nowplaying

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.NowPlayingPreferences
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val playerPane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Now playing")

    private fun setContent() {
        composeRule.setContent {
            MediatheqTheme {
                NowPlayingLayout(state, MutableStateFlow(150.seconds), { actions += it }, content = { mini ->
                    Column {
                        Text(SHELL_TEXT)
                        mini(true)
                    }
                })
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
    fun `an auto expand request opens the full player and is acknowledged`() {
        var autoExpand by mutableStateOf(false)
        composeRule.setContent {
            MediatheqTheme {
                NowPlayingLayout(
                    state,
                    MutableStateFlow(150.seconds),
                    { actions += it },
                    content = { mini -> mini(true) },
                    autoExpand = autoExpand,
                )
            }
        }
        composeRule.onNodeWithContentDescription(closeButton).assertDoesNotExist()

        autoExpand = true
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(closeButton).assertIsDisplayed()
        assertThat(actions).containsExactly(NowPlayingAction.AutoExpanded)
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
    fun `shuffle and repeat show their state to TalkBack and send their actions`() {
        state = playing.copy(shuffle = true, repeatMode = RepeatMode.One)
        setContent()
        composeRule.onNode(miniPlayer).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Shuffle").assertIsOn().performClick()
        composeRule
            .onNodeWithContentDescription("Repeat")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "This track"))
            .performClick()

        assertThat(
            actions,
        ).containsExactly(NowPlayingAction.SetShuffle(false), NowPlayingAction.SetRepeatMode(RepeatMode.Off)).inOrder()
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

    @Test
    fun `swiping the mini player up opens the player, swiping the player down closes it`() {
        setContent()

        composeRule.onNode(miniPlayer).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(closeButton).assertIsDisplayed()

        composeRule.onNode(playerPane).performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(closeButton).assertDoesNotExist()
        composeRule.onNode(miniPlayer).assertIsDisplayed()
    }

    @Test
    fun `while the player is open, TalkBack cannot reach the shell behind it`() {
        setContent()
        composeRule
            .onNodeWithText(SHELL_TEXT)
            .assert(!hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)))

        composeRule.onNode(miniPlayer).performClick()
        composeRule.waitForIdle()

        // Still composed (it stays behind), but marked hidden for accessibility services.
        composeRule
            .onNodeWithText(SHELL_TEXT)
            .assert(hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)))
        composeRule.onNode(playerPane).assertExists()
    }

    @Test
    fun `the queue opens from the player, and back closes the queue before the player`() {
        val queuePane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Queue")
        setContent()
        composeRule.onNode(miniPlayer).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Open queue").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(queuePane).assertExists()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNode(queuePane).assertDoesNotExist()
        composeRule.onNode(playerPane).assertExists()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNode(playerPane).assertDoesNotExist()
    }

    @Test
    fun `with lyrics in place of the cover the player still closes to the mini player`() {
        state = playing.copy(lyricsShown = true)
        setContent()
        composeRule.onNode(miniPlayer).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("No lyrics")).assertExists()

        composeRule.onNodeWithContentDescription(closeButton).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(playerPane).assertDoesNotExist()
        composeRule.onNode(miniPlayer).assertIsDisplayed()
    }

    @Test
    fun `the artist and the equalizer open behind the player, which closes`() {
        val artist = Artist(MediaId(ServerId("s"), "ar1"), "deadmau5")
        state = playing.copy(artists = listOf(artist))
        val opened = mutableListOf<NavKey>()
        composeRule.setContent {
            MediatheqTheme {
                NowPlayingLayout(
                    state,
                    MutableStateFlow(150.seconds),
                    { actions += it },
                    content = { mini -> mini(true) },
                    onNavigate = { opened += it },
                )
            }
        }
        composeRule.onNode(miniPlayer).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasText("deadmau5") and hasClickAction() and hasAnyAncestor(playerPane)).performClick()
        composeRule.waitForIdle()

        assertThat(opened).containsExactly(ArtistKey(artist.id))
        composeRule.onNode(playerPane).assertDoesNotExist()
    }

    @Test
    fun `bit-perfect shows its sample rate and the volume note on the player`() {
        state = playing.copy(bitPerfectSampleRateHz = 96_000)
        setContent()
        composeRule.onNode(miniPlayer).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasText("Bit-perfect · 96 kHz")).assertExists()
        composeRule.onNode(hasText("Phone volume does not apply")).assertExists()
    }

    @Test
    fun `without bit-perfect there is no indicator`() {
        setContent()
        composeRule.onNode(miniPlayer).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasText("Phone volume does not apply")).assertDoesNotExist()
    }

    @Test
    fun `swipe to skip on the mini player plays the next track, and a swipe up still opens the player`() {
        setContent()

        composeRule.onNode(miniPlayer).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertThat(actions).contains(NowPlayingAction.Next)
        composeRule.onNode(playerPane).assertDoesNotExist()

        composeRule.onNode(miniPlayer).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNode(playerPane).assertExists()
    }

    @Test
    fun `with swipe to skip off a sideways swipe on the mini player skips nothing`() {
        state = playing.copy(settings = NowPlayingPreferences(miniSwipeToSkip = false))
        setContent()

        composeRule.onNode(miniPlayer).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertThat(actions).doesNotContain(NowPlayingAction.Next)
    }

    private companion object {
        const val SETTLE_FRAMES = 180
        const val SHELL_TEXT = "Shell content"
    }
}
