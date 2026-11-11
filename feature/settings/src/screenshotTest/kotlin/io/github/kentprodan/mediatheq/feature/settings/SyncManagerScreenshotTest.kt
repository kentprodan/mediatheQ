package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.SyncRecord
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val finished = Instant.fromEpochSeconds(1_760_000_000)
private val success = SyncRecord(finished, null, 3120, 24.seconds)

private val home =
    SyncCard(
        "mediatheq.db",
        "Home",
        "Ana",
        "http://192.168.178.34:8096",
        active = true,
        connection = Connection.Online,
        last = success,
        ago = 5.minutes,
        running = null,
    )
private val office =
    SyncCard(
        "mediatheq-2.db",
        "Office",
        "Bo",
        "https://music.example.org",
        active = false,
        connection = Connection.Checking,
        last = null,
        ago = null,
        running = null,
    )

/** The sheet's content on the sheet's own container (the sheet itself is a popup: blank in screenshot tests). */
@Composable
private fun Sheet(state: SyncManagerUiState) {
    MediatheqTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { SyncManagerContent(state, {}) }
    }
}

/** Idle: the active account synced 5 minutes ago, the other never, its server still being asked. */
@PreviewTest
@MqPreviews
@Composable
fun SyncManagerIdleScreenshot() {
    Sheet(SyncManagerUiState.Content(listOf(home, office.copy(connection = Connection.Online)), null, false))
}

/** A sync running: its step and progress; Sync all disabled meanwhile. */
@PreviewTest
@MqPreviews
@Composable
fun SyncManagerRunningScreenshot() {
    val running = home.copy(running = SyncProgress(SyncStep.Tracks, 1500, 3120))
    Sheet(SyncManagerUiState.Content(listOf(running, office), null, true))
}

/** The last sync failed: the error in words. */
@PreviewTest
@MqPreviews
@Composable
fun SyncManagerFailedScreenshot() {
    val failed = home.copy(last = SyncRecord(finished, MqError.Unauthorized, 0, 3.seconds), ago = 2.hours)
    Sheet(SyncManagerUiState.Content(listOf(failed), null, false))
}

/** No network: every server offline, Sync all disabled with the reason. */
@PreviewTest
@MqPreviews
@Composable
fun SyncManagerOfflineScreenshot() {
    val cards = listOf(home, office).map { it.copy(connection = Connection.Offline) }
    Sheet(SyncManagerUiState.Content(cards, OfflineReason.NoNetwork, false))
}
