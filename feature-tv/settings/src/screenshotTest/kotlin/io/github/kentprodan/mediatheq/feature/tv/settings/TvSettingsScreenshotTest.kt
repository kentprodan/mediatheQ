package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.APP_ENTRY
import io.github.kentprodan.mediatheq.core.common.LicenseEntry
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.DefaultTvCarouselSources
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvRoPreview
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind

private val settings =
    TvSettingsUiState.Content(
        hdmi = TvHdmiOutput(OutputId(OutputKind.Hdmi, "lgtv"), "LG TV", atmosPassthrough = true),
        quality = StreamingQuality.Original,
        sync = SyncStatus.Idle(null),
        serverName = "Home server",
        userName = "kent",
        carouselPicked = DefaultTvCarouselSources,
    )

@Composable
private fun Settings(
    state: TvSettingsUiState,
    vararg levels: SettingsPage,
) = MediatheqTvTheme {
    TvSettingsScreen(state, "0.1.0", {}, {}, {}, {}, {}, start = listOf(SettingsPage.Root) + levels)
}

/** The categories (#580), the first one's level previewed on the right. */
@PreviewTest
@MqTvPreviews
@MqTvRoPreview
@Composable
fun TvSettingsScreenshot() = Settings(settings)

/** Before the first play over HDMI: the switch row is disabled and says why. */
@PreviewTest
@MqTvPreviews
@MqTvRoPreview
@Composable
fun TvSettingsSoundScreenshot() = Settings(settings.copy(hdmi = null), SettingsPage.Sound)

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsSoundHdmiScreenshot() = Settings(settings, SettingsPage.Sound)

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsVideoScreenshot() = Settings(settings.copy(appMode = AppMode.Both), SettingsPage.Video)

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsDisplayScreenshot() = Settings(settings.copy(posterAccent = true), SettingsPage.Display)

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsHomeScreenshot() = Settings(settings, SettingsPage.Home)

/** A sync runs. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsLibrarySyncingScreenshot() =
    Settings(
        settings.copy(appMode = AppMode.Both, sync = SyncStatus.Running(SyncStep.Albums, 120, 800)),
        SettingsPage.Library,
    )

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsLibrarySyncFailedScreenshot() =
    Settings(settings.copy(sync = SyncStatus.Failed(MqError.Network, null)), SettingsPage.Library)

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsAccountScreenshot() = Settings(settings.copy(appMode = AppMode.Both), SettingsPage.Account)

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsAboutScreenshot() = Settings(settings, SettingsPage.About)

/** A level that picks one value: a radio on the current one. */
@PreviewTest
@MqTvPreviews
@MqTvRoPreview
@Composable
fun TvSettingsQualityScreenshot() =
    Settings(settings.copy(quality = StreamingQuality.Kbps256), SettingsPage.Sound, SettingsPage.Quality)

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsCarouselSourcesScreenshot() =
    Settings(settings.copy(carouselSources = DefaultTvCarouselSources), SettingsPage.Home, SettingsPage.CarouselSources)

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsSignOutScreenshot() = Settings(settings, SettingsPage.Account, SettingsPage.SignOut)

private val okhttp =
    LicenseEntry(
        id = "com.squareup.okhttp3:okhttp",
        name = "okhttp",
        detail = "5.1.0",
        licenseName = "Apache-2.0",
        licenseText =
            "Apache License\nVersion 2.0, January 2004\n\n" +
                "TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION",
        url = "https://square.github.io/okhttp/",
    )

@PreviewTest
@MqTvPreviews
@Composable
fun TvLicensesScreenshot() =
    MediatheqTvTheme { TvLicensesScreen(TvLicensesUiState.Content(listOf(APP_ENTRY, okhttp)), {}) }

@PreviewTest
@MqTvPreviews
@Composable
fun TvLicenseTextScreenshot() = MediatheqTvTheme { TvLicenseScreen(okhttp.copy(notice = "okhttp notice")) }

/** The PIN entry (#434), its content in place (popups render blank in screenshots). */
@PreviewTest
@MqTvPreviews
@Composable
fun TvPinEntryScreenshot() =
    MediatheqTvTheme {
        androidx.tv.material3.Surface {
            io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvPinContent(
                "Enter the PIN",
                "PIN",
                "12",
                "Wrong PIN",
            )
        }
    }
