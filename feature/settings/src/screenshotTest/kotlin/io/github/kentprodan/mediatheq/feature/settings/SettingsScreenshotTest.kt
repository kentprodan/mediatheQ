package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.AppFont
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.ColorSource
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.ThemeMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqColorSource
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqSeedPresets
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeMode
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeOptions

private val content =
    SettingsUiState.Content(
        "Home server",
        "Ana",
        AudioPreferences(meteredQuality = StreamingQuality.Kbps128),
        LibraryPreferences(albumColumns = 3, ratingMode = RatingMode.HalfStars),
    )

/** The hub: Settings, Miscellaneous and About groups. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsHubScreenshot() {
    MediatheqTheme { SettingsHubScreen(onOpen = {}, onBack = {}) }
}

@Composable
private fun Page(page: SettingsPage) {
    MediatheqTheme { SettingsPageScreen(page, content, {}, {}, {}) }
}

/** Interface: colors, album grid, ratings. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsInterfaceScreenshot() = Page(SettingsPage.Interface)

/** Playback: qualities per network, normalization, the Audio page. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsPlaybackScreenshot() = Page(SettingsPage.Playback)

/** Offline, cache, and download. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsStorageScreenshot() = Page(SettingsPage.Storage)

/** Manage media providers: the account and Log out. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsProvidersScreenshot() = Page(SettingsPage.Providers)

/** About: version, licenses, showcase. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsAboutScreenshot() {
    MediatheqTheme { AboutSettingsScreen("0.1.0", {}, {}, {}) }
}

@Composable
private fun ThemePage(
    theme: ThemePreferences = ThemePreferences(),
    options: MqThemeOptions = MqThemeOptions(),
) {
    MediatheqTheme(options) {
        SettingsPageScreen(SettingsPage.Theme, content.copy(library = content.library.copy(theme = theme)), {}, {}, {})
    }
}

/** Theme, font and colors: Iris, system mode. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsThemeScreenshot() = ThemePage()

/** Theme, font and colors with a custom color: presets, hue slider, the generated scheme's swatches. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsThemeCustomScreenshot() {
    val teal = MqSeedPresets[2]
    ThemePage(
        ThemePreferences(colorSource = ColorSource.Custom, customSeed = teal),
        MqThemeOptions(colorSource = MqColorSource.Custom, customSeed = teal),
    )
}

/** Theme, font and colors in Black mode. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsThemeBlackScreenshot() =
    ThemePage(ThemePreferences(mode = ThemeMode.Black), MqThemeOptions(mode = MqThemeMode.Black))

/** The Now playing color source dialog's content, Cover selected. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsNowPlayingColorsDialogScreenshot() {
    MediatheqTheme { ThemeChoiceDialogContent(ThemeDialog.NowPlayingColors, ThemePreferences()) }
}

/** The typography dialog's content, Lato selected (fonts show in the system font: layoutlib cannot fetch them). */
@PreviewTest
@MqPreviews
@Composable
fun SettingsTypographyDialogScreenshot() {
    MediatheqTheme { ThemeChoiceDialogContent(ThemeDialog.Font, ThemePreferences(font = AppFont.Lato)) }
}
