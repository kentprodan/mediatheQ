package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

private val content =
    SettingsUiState.Content(
        "Home server",
        "Ana",
        AudioPreferences(meteredQuality = StreamingQuality.Kbps128),
        LibraryPreferences(albumColumns = 3, ratingMode = RatingMode.HalfStars),
    )

/** The top of the screen: account and playback. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsScreenshot() {
    MediatheqTheme { SettingsScreen(content, "0.1.0", {}, {}, {}, {}, {}) }
}

@Composable
private fun Groups(groups: @Composable () -> Unit) {
    MediatheqTheme {
        Surface { Column(Modifier.padding(horizontal = MediatheqSpacing.l)) { groups() } }
    }
}

/** Account and Playback (qualities per network, normalization, the Audio page). */
@PreviewTest
@MqPreviews
@Composable
fun SettingsAccountPlaybackScreenshot() {
    Groups {
        AccountGroup(content) {}
        PlaybackGroup(content.audio, {}) {}
    }
}

/** Downloads and storage, Appearance. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsStorageAppearanceScreenshot() {
    Groups {
        StorageGroup(content.audio, content.library) {}
        AppearanceGroup(content.library) {}
    }
}

/** Ratings and About. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsRatingsAboutScreenshot() {
    Groups {
        RatingsGroup(content.library.ratingMode) {}
        AboutGroup("0.1.0", onOpenShowcase = {}, onOpenLicenses = {})
    }
}
