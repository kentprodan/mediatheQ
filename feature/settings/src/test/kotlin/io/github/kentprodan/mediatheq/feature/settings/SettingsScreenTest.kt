package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<SettingsAction>()
    private val events = mutableListOf<String>()

    private fun show(content: SettingsUiState.Content = SettingsUiState.Content("Home", "Ana")) {
        composeRule.setContent {
            MediatheqTheme {
                SettingsScreen(
                    content,
                    "1.2.3",
                    { actions += it },
                    onOpenAudio = { events += "audio" },
                    onOpenShowcase = { events += "showcase" },
                    onBack = { events += "back" },
                )
            }
        }
    }

    private fun scrollTo(text: String) =
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))

    @Test
    fun `the account shows, and log out sends its action`() {
        show()

        composeRule.onNodeWithText("Home").assertExists()
        composeRule.onNodeWithText("Ana").assertExists()
        composeRule.onNodeWithText("Log out").performClick()

        assertThat(actions).containsExactly(SettingsAction.LogOut)
    }

    @Test
    fun `each network has its own quality chips, the current one selected`() {
        show(SettingsUiState.Content("Home", "Ana", AudioPreferences(meteredQuality = StreamingQuality.Kbps128)))

        composeRule.onAllNodesWithText("Original")[0].assertIsSelected()
        composeRule.onAllNodesWithText("128 kbps")[1].assertIsSelected()
        composeRule.onAllNodesWithText("256 kbps")[0].performClick()

        assertThat(
            actions,
        ).containsExactly(SettingsAction.SetStreamingQuality(metered = false, StreamingQuality.Kbps256))
    }

    @Test
    fun `switches are whole rows with their state`() {
        show()
        scrollTo("Download on Wi-Fi only")

        composeRule.onNode(isToggleable() and hasText("Download on Wi-Fi only")).assertIsOn().performClick()

        assertThat(actions).containsExactly(SettingsAction.SetDownloadsUnmeteredOnly(false))
    }

    @Test
    fun `the rating choice sends the mode`() {
        show()
        scrollTo("Half stars")

        composeRule.onNodeWithText("Half stars").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetRatingMode(RatingMode.HalfStars))
    }

    @Test
    fun `audio, the showcase and back open their pages`() {
        show()

        composeRule.onNodeWithText("Audio").performClick()
        scrollTo("Theme showcase")
        composeRule.onNodeWithText("Theme showcase").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(events).containsExactly("audio", "showcase", "back").inOrder()
    }

    @Test
    fun `the version shows under About`() {
        show()
        scrollTo("1.2.3")

        composeRule.onNodeWithText("1.2.3").assertExists()
    }

    @Test
    fun `group titles are headings`() {
        show()

        composeRule.onNode(isHeading() and hasText("Account")).assertExists()
        composeRule.onNode(isHeading() and hasText("Playback")).assertExists()
    }
}
