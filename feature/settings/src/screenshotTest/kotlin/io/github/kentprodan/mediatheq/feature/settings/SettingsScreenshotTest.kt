package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

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
