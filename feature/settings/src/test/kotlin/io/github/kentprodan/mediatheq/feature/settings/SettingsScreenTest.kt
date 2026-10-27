package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
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

    private fun show(
        page: SettingsPage,
        content: SettingsUiState = SettingsUiState.Content("Home", "Ana"),
    ) {
        composeRule.setContent {
            MediatheqTheme {
                SettingsPageScreen(
                    page,
                    content,
                    { actions += it },
                    onOpenAudio = { events += "audio" },
                    onBack = { events += "back" },
                )
            }
        }
    }

    private fun showAbout() {
        composeRule.setContent {
            MediatheqTheme {
                AboutSettingsScreen(
                    "1.2.3",
                    onOpenShowcase = { events += "showcase" },
                    onOpenLicenses = { events += "licenses" },
                    onBack = { events += "back" },
                )
            }
        }
    }

    /** The titles of a page's rows, so every setting is on exactly one page. */
    private fun assertRows(vararg titles: String) {
        titles.forEach { title ->
            scrollTo(title)
            composeRule.onAllNodesWithText(title).assertCountEquals(1)
        }
    }

    private fun scrollTo(text: String) =
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))

    @Test
    fun `manage media providers shows the account, and log out sends its action`() {
        show(SettingsPage.Providers)

        composeRule.onNodeWithText("Home").assertExists()
        composeRule.onNodeWithText("Ana").assertExists()
        composeRule.onNodeWithText("Log out").performClick()

        assertThat(actions).containsExactly(SettingsAction.LogOut)
    }

    @Test
    fun `each network has its own quality chips, the current one selected`() {
        show(
            SettingsPage.Playback,
            SettingsUiState.Content("Home", "Ana", AudioPreferences(meteredQuality = StreamingQuality.Kbps128)),
        )

        composeRule.onAllNodesWithText("Original")[0].assertIsSelected()
        composeRule.onAllNodesWithText("128 kbps")[1].assertIsSelected()
        composeRule.onAllNodesWithText("256 kbps")[0].performClick()

        assertThat(
            actions,
        ).containsExactly(SettingsAction.SetStreamingQuality(metered = false, StreamingQuality.Kbps256))
    }

    @Test
    fun `switches are whole rows with their state`() {
        show(SettingsPage.Storage)
        scrollTo("Download on Wi-Fi only")

        composeRule.onNode(isToggleable() and hasText("Download on Wi-Fi only")).assertIsOn().performClick()

        assertThat(actions).containsExactly(SettingsAction.SetDownloadsUnmeteredOnly(false))
    }

    @Test
    fun `the offline mode switch sends its new state`() {
        show(SettingsPage.Storage)
        scrollTo("Offline mode")

        composeRule.onNode(isToggleable() and hasText("Offline mode")).assertIsOff().performClick()

        assertThat(actions).containsExactly(SettingsAction.SetOfflineMode(true))
    }

    @Test
    fun `the rating choice sends the mode`() {
        show(SettingsPage.Interface)
        scrollTo("Half stars")

        composeRule.onNodeWithText("Half stars").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetRatingMode(RatingMode.HalfStars))
    }

    @Test
    fun `playback opens the audio page, and back works`() {
        show(SettingsPage.Playback)

        composeRule.onNodeWithText("Audio").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(events).containsExactly("audio", "back").inOrder()
    }

    @Test
    fun `about shows the version and opens the licenses and the showcase`() {
        showAbout()

        composeRule.onNodeWithText("1.2.3").assertExists()
        composeRule.onNodeWithText("Open-source licenses").performClick()
        composeRule.onNodeWithText("Theme showcase").performClick()

        assertThat(events).containsExactly("licenses", "showcase").inOrder()
    }

    @Test
    fun `each page holds its own settings`() {
        show(SettingsPage.Interface)
        assertRows("Material You colors", "Album grid columns", "Star ratings")
    }

    @Test
    fun `the playback page holds the qualities, normalization and audio`() {
        show(SettingsPage.Playback)
        assertRows("Quality on Wi-Fi", "Quality on mobile data", "Volume normalization", "Audio")
    }

    @Test
    fun `the storage page holds offline mode, downloads and the cache`() {
        show(SettingsPage.Storage)
        assertRows("Offline mode", "Download on Wi-Fi only", "Streaming cache")
    }

    @Test
    fun `the providers page holds the account and log out`() {
        show(SettingsPage.Providers)
        assertRows("Server", "Signed in as", "Log out")
    }

    @Test
    fun `a page shows nothing until its settings are read`() {
        show(SettingsPage.Interface, SettingsUiState.Loading)

        composeRule.onNodeWithText("Material You colors").assertDoesNotExist()
    }

    @Test
    fun `group titles are headings`() {
        show(SettingsPage.Interface)

        composeRule.onNode(isHeading() and hasText("Appearance")).assertExists()
        composeRule.onNode(isHeading() and hasText("Ratings")).assertExists()
    }
}
