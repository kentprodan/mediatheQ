package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.APP_ENTRY
import io.github.kentprodan.mediatheq.core.common.LicenseEntry
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind

private val settings =
    TvSettingsUiState.Content(
        hdmi = TvHdmiOutput(OutputId(OutputKind.Hdmi, "lgtv"), "LG TV", atmosPassthrough = true),
        quality = StreamingQuality.Original,
        sync = SyncStatus.Idle(null),
        serverName = "Home server",
        userName = "kent",
    )

@Composable
private fun Settings(state: TvSettingsUiState) = MediatheqTvTheme { TvSettingsScreen(state, "0.1.0", {}, {}) }

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsScreenshot() = Settings(settings)

/** Before the first play over HDMI: the switch row is disabled and says why; a sync runs. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsNoHdmiSyncingScreenshot() =
    Settings(settings.copy(hdmi = null, sync = SyncStatus.Running(SyncStep.Albums, 120, 800)))

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsSyncFailedScreenshot() = Settings(settings.copy(sync = SyncStatus.Failed(MqError.Network, null)))

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsQualityDialogScreenshot() =
    Settings(settings.copy(quality = StreamingQuality.Kbps256, dialog = TvSettingsDialog.Quality))

@PreviewTest
@MqTvPreviews
@Composable
fun TvSettingsSignOutDialogScreenshot() = Settings(settings.copy(dialog = TvSettingsDialog.SignOut))

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
