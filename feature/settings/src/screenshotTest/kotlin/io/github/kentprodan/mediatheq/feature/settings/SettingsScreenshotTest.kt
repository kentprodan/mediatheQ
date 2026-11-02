package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.AppFont
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.ColorSource
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.MainNavEntry
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.PortraitNavStyle
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.ThemeMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.data.TrackAction
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

/** The hub with Downloads out of the navigation: "Manage offline files" shows (#122). */
@PreviewTest
@MqPreviews
@Composable
fun SettingsHubManageOfflineFilesScreenshot() {
    MediatheqTheme { SettingsHubScreen(onOpen = {}, onBack = {}, showsManageOfflineFiles = true) }
}

/** Navigation and media start with some choices made: six entries, floating toolbar + FAB, 50 % transparency. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsNavigationScreenshot() {
    val navigation =
        NavigationPreferences(
            entries =
                listOf(
                    MainNavEntry.Home,
                    MainNavEntry.Library,
                    MainNavEntry.Albums,
                    MainNavEntry.Tracks,
                    MainNavEntry.Downloads,
                    MainNavEntry.Search,
                ),
            portraitStyle = PortraitNavStyle.FloatingToolbarWithFab,
            transparencyPercent = 50,
            trackAction = TrackAction.PlayNext,
        )
    MediatheqTheme {
        SettingsPageScreen(
            SettingsPage.Navigation,
            content.copy(
                library = content.library.copy(navigation = navigation),
            ),
            {
            },
            {},
            {},
        )
    }
}

/** "Organize your navigation": the default four checked first, the rest below. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsNavEntriesDialogScreenshot() {
    MediatheqTheme { NavEntriesDialogContent(NavEntriesDraft.of(NavigationPreferences.DEFAULT_ENTRIES)) }
}

/** Six entries checked: the others cannot be checked. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsNavEntriesDialogFullScreenshot() {
    val six =
        listOf(
            MainNavEntry.Home,
            MainNavEntry.Tracks,
            MainNavEntry.Albums,
            MainNavEntry.AlbumArtists,
            MainNavEntry.Playlists,
            MainNavEntry.Search,
        )
    MediatheqTheme { NavEntriesDialogContent(NavEntriesDraft.of(six)) }
}

/** The default track action dialog's content, Play selected. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsTrackActionDialogScreenshot() {
    MediatheqTheme { NavigationChoiceDialogContent(NavigationDialog.TrackAction, NavigationPreferences()) }
}

/** The portrait navigation style dialog's content, Bottom bar selected. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsPortraitStyleDialogScreenshot() {
    MediatheqTheme { NavigationChoiceDialogContent(NavigationDialog.PortraitStyle, NavigationPreferences()) }
}
