package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.BackupAccountSummary
import io.github.kentprodan.mediatheq.core.data.BackupSummary
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import kotlin.time.Instant

@Composable
private fun Backup(state: BackupUiState) = MediatheqTheme { BackupScreen(state, {}, {}, {}, {}) }

/** The note, Create a backup and Pick a backup to restore. */
@PreviewTest
@MqPreviews
@Composable
fun BackupScreenshot() = Backup(BackupUiState())

/** Restored, two servers to sign in to. */
@PreviewTest
@MqPreviews
@Composable
fun BackupRestoredScreenshot() = Backup(BackupUiState(message = BackupMessage.Restored(2)))

/** A file that is no backup: nothing changed. */
@PreviewTest
@MqPreviews
@Composable
fun BackupInvalidScreenshot() = Backup(BackupUiState(message = BackupMessage.Invalid))

/** The restore confirmation, in place (dialogs render blank in screenshot tests). */
@PreviewTest
@MqPreviews
@Composable
fun BackupRestoreSummaryScreenshot() =
    MediatheqTheme {
        MqDialogContent("Restore this backup?", "Restore", {}, "Cancel", {}) {
            RestoreSummary(
                BackupSummary(
                    Instant.parse("2026-11-12T12:00:00Z"),
                    "1.4.0",
                    186,
                    listOf(
                        BackupAccountSummary("Home", "Ana", "http://192.168.178.34:8096"),
                        BackupAccountSummary("Office", "Bo", "https://music.example.org"),
                    ),
                ),
            )
        }
    }
