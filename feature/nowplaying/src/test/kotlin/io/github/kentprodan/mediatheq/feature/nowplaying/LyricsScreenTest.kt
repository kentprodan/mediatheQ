package io.github.kentprodan.mediatheq.feature.nowplaying

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class LyricsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<NowPlayingAction>()
    private var closed = false
    private var position by mutableStateOf(Duration.ZERO)

    /** "Line 0" at 0 s, "Line 1" at 5 s, ... */
    private val synced = Lyrics((0 until 60).map { LyricLine("Line $it", (it * 5).seconds) })

    private fun setContent(
        state: LyricsUiState,
        canSeek: Boolean = true,
    ) {
        composeRule.setContent {
            MediatheqTheme {
                LyricsScreen(state, { position }, canSeek, onAction = { actions += it }, onClose = { closed = true })
            }
        }
    }

    @Test
    fun `tapping a synced line seeks to its start, and the back button closes`() {
        setContent(LyricsUiState.Content(synced))

        composeRule
            .onNodeWithText("Line 2")
            .assert(SemanticsMatcher("play label") { it.config[SemanticsActions.OnClick].label == "Play from here" })
            .performClick()
        composeRule.onNodeWithContentDescription("Close lyrics").performClick()

        assertThat(actions).containsExactly(NowPlayingAction.SeekToTime(10.seconds))
        assertThat(closed).isTrue()
    }

    @Test
    fun `R12 lines cannot be tapped when the stream cannot be seeked`() {
        setContent(LyricsUiState.Content(synced), canSeek = false)

        composeRule.onNodeWithText("Line 2").assertHasNoClickAction()
    }

    @Test
    fun `plain lines cannot be tapped`() {
        setContent(LyricsUiState.Content(Lyrics(listOf(LyricLine("Only words")))))

        composeRule.onNodeWithText("Only words").assertHasNoClickAction()
    }

    @Test
    fun `the line being sung is scrolled into view`() {
        setContent(LyricsUiState.Content(synced))
        composeRule.onNodeWithText("Line 40").assertDoesNotExist()

        position = 201.seconds
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Line 40").assertIsDisplayed()
    }

    @Test
    fun `a manual scroll stops the following for a few seconds`() {
        setContent(LyricsUiState.Content(synced))
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Line 1").assertIsNotDisplayed()

        composeRule.mainClock.autoAdvance = false
        position = 6.seconds
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("Line 1").assertIsNotDisplayed()

        composeRule.mainClock.advanceTimeBy(4_000)
        composeRule.onNodeWithText("Line 1").assertIsDisplayed()
    }

    @Test
    fun `no lyrics says so`() {
        setContent(LyricsUiState.None)

        composeRule.onNodeWithText("No lyrics").assertIsDisplayed()
    }

    @Test
    fun `an error offers to try again`() {
        setContent(LyricsUiState.Error(MqError.Network))

        composeRule.onNodeWithText("Try again").performClick()

        assertThat(actions).containsExactly(NowPlayingAction.RetryLyrics)
    }
}
