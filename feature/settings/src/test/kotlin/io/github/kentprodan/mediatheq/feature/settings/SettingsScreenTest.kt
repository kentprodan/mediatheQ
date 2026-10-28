package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AppFont
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.ColorSource
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.NowPlayingColors
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.ThemeMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqSeedPresets
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
                    onOpenTheme = { events += "theme" },
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
        assertRows("Theme, font and colors", "Album grid columns", "Star ratings")
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

        composeRule.onNodeWithText("Theme, font and colors").assertDoesNotExist()
    }

    @Test
    fun `group titles are headings`() {
        show(SettingsPage.Interface)

        composeRule.onNode(isHeading() and hasText("Appearance")).assertExists()
        composeRule.onNode(isHeading() and hasText("Ratings")).assertExists()
    }

    @Test
    fun `the interface page opens Theme, font and colors`() {
        show(SettingsPage.Interface)

        composeRule.onNodeWithText("Theme, font and colors").performClick()

        assertThat(events).containsExactly("theme")
    }

    @Test
    fun `the theme page holds the theme, Now playing, typography and restore settings`() {
        show(SettingsPage.Theme)
        assertRows(
            "Theme mode",
            "Default color source",
            "Selected theme colors",
            "Now playing color source",
            "Ignore background and surfaces",
            "Instant color updates",
            "Restore defaults",
        )
    }

    @Test
    fun `a theme choice sends every theme setting with the one changed`() {
        show(SettingsPage.Theme)

        composeRule.onNodeWithText("Black").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetTheme(ThemePreferences(mode = ThemeMode.Black)))
    }

    @Test
    fun `the custom color shows only with the custom color source`() {
        show(SettingsPage.Theme)
        composeRule.onNodeWithContentDescription("Hue").assertDoesNotExist()

        composeRule.onNodeWithText("Custom color").performClick()
        assertThat(actions).containsExactly(SettingsAction.SetTheme(ThemePreferences(colorSource = ColorSource.Custom)))
    }

    @Test
    fun `a preset seed sets the custom color`() {
        val theme = ThemePreferences(colorSource = ColorSource.Custom)
        show(SettingsPage.Theme, SettingsUiState.Content("Home", "Ana", library = LibraryPreferences(theme = theme)))

        composeRule.onNodeWithContentDescription("Color 3 of 8").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetTheme(theme.copy(customSeed = MqSeedPresets[2])))
    }

    @Test
    fun `the Now playing dialog shows the current choice, and a tap applies another and closes`() {
        show(SettingsPage.Theme)
        scrollTo("Now playing color source")

        composeRule.onNodeWithText("Now playing color source").performClick()
        composeRule.onNode(isSelectable() and hasText("Cover")).assertIsSelected()
        composeRule.onNodeWithText("Cover vibrant").performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetTheme(ThemePreferences(nowPlayingColors = NowPlayingColors.CoverVibrant)),
            )
        composeRule.onNodeWithText("Cover vibrant").assertDoesNotExist()
    }

    @Test
    fun `the typography dialog sets the font`() {
        show(SettingsPage.Theme)
        scrollTo("Typography")

        composeRule.onAllNodesWithText("Typography").filterToOne(hasClickAction()).performClick()
        composeRule.onNodeWithText("Lato").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetTheme(ThemePreferences(font = AppFont.Lato)))
    }

    @Test
    fun `restore defaults asks first, and Cancel changes nothing`() {
        show(SettingsPage.Theme)
        scrollTo("Restore defaults")

        composeRule.onNodeWithText("Restore defaults").performClick()
        composeRule.onNodeWithText("Restore all options on this page to their default value?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(actions).isEmpty()
        composeRule.onNodeWithText("Restore all options on this page to their default value?").assertDoesNotExist()
    }

    @Test
    fun `restore defaults resets the page after confirming`() {
        show(SettingsPage.Theme)
        scrollTo("Restore defaults")

        composeRule.onNodeWithText("Restore defaults").performClick()
        // The group title says "Restore" too: the dialog's button is the clickable one.
        composeRule.onNode(hasText("Restore") and hasClickAction()).performClick()

        assertThat(actions).containsExactly(SettingsAction.RestoreThemeDefaults)
    }
}
