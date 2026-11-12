package io.github.kentprodan.mediatheq.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.BackupSummary
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun BackupRoute(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val create =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri ->
            uri?.let { viewModel.onAction(BackupAction.Write(it.toString())) }
        }
    // Any type: some file managers label a .json file as plain text or octet-stream.
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.onAction(BackupAction.Pick(it.toString())) }
        }
    BackupScreen(
        state,
        viewModel::onAction,
        onCreate = { create.launch("mediatheq-backup-${LocalDate.now()}.json") },
        onPick = { open.launch(arrayOf(JSON, "text/plain", "application/octet-stream")) },
        onBack = onBack,
    )
}

/**
 * Settings -> Backup & restore (#150): the note, Create a backup, Pick a backup to restore, how the last one went; the
 * restore asks first with what the backup holds. Stateless.
 */
@Composable
fun BackupScreen(
    state: BackupUiState,
    onAction: (BackupAction) -> Unit,
    onCreate: () -> Unit,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(R.string.settings_backup), onBack) {
        item(key = "note") {
            Text(
                stringResource(R.string.settings_backup_note),
                Modifier.padding(top = MediatheqSpacing.s),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "actions") {
            Group(R.string.settings_backup) {
                item { shapes ->
                    SegmentedListItem(
                        onClick = onCreate,
                        shapes = shapes,
                        enabled = !state.busy,
                        colors = settingsColors(),
                        leadingContent = { Icon(MqIcons.Download, contentDescription = null) },
                        supportingContent = { Text(stringResource(R.string.settings_backup_create_summary)) },
                    ) { Text(stringResource(R.string.settings_backup_create)) }
                }
                item { shapes ->
                    SegmentedListItem(
                        onClick = onPick,
                        shapes = shapes,
                        enabled = !state.busy,
                        colors = settingsColors(),
                        leadingContent = { Icon(MqIcons.Restore, contentDescription = null) },
                        supportingContent = { Text(stringResource(R.string.settings_backup_pick_summary)) },
                    ) { Text(stringResource(R.string.settings_backup_pick)) }
                }
            }
        }
        state.message?.let { message ->
            item(key = "message") { MessageCard(message) { onAction(BackupAction.DismissMessage) } }
        }
    }
    state.pending?.let { pending ->
        MqDialog(
            title = stringResource(R.string.settings_backup_restore_title),
            confirmLabel = stringResource(R.string.settings_backup_restore),
            onConfirm = { onAction(BackupAction.ConfirmRestore) },
            dismissLabel = stringResource(R.string.settings_cancel),
            onDismiss = { onAction(BackupAction.DismissRestore) },
        ) { RestoreSummary(pending.summary) }
    }
}

/** What the picked backup holds: when and by which version it was made, its settings, its accounts. */
@Composable
internal fun RestoreSummary(summary: BackupSummary) {
    val date =
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .format(
                summary.created
                    .toEpochMilliseconds()
                    .let {
                        java.time.Instant.ofEpochMilli(
                            it,
                        )
                    }.atZone(ZoneId.systemDefault()),
            )
    Column(verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
        Text(
            stringResource(R.string.settings_backup_made, date, summary.appVersion),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            pluralStringResource(R.plurals.settings_backup_settings, summary.settingsCount, summary.settingsCount),
            style = MaterialTheme.typography.bodyMedium,
        )
        summary.accounts.forEach {
            Text(
                stringResource(R.string.settings_backup_account, it.serverName, it.userName, it.address),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.settings_backup_replace), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MessageCard(
    message: BackupMessage,
    onDismiss: () -> Unit,
) {
    val failed = message !is BackupMessage.Saved && message !is BackupMessage.Restored
    Surface(
        Modifier.fillMaxWidth().padding(top = MediatheqSpacing.l),
        shape = MaterialTheme.shapes.large,
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(MediatheqSpacing.l)) {
            Text(
                message.text(),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_ok)) }
        }
    }
}

@Composable
private fun BackupMessage.text(): String =
    when (this) {
        BackupMessage.Saved -> {
            stringResource(R.string.settings_backup_saved)
        }

        BackupMessage.NotWritten -> {
            stringResource(R.string.settings_backup_not_written)
        }

        is BackupMessage.Restored -> {
            if (toSignIn == 0) {
                stringResource(R.string.settings_backup_restored)
            } else {
                pluralStringResource(R.plurals.settings_backup_restored_sign_in, toSignIn, toSignIn)
            }
        }

        BackupMessage.Unreadable -> {
            stringResource(R.string.settings_backup_unreadable)
        }

        BackupMessage.Invalid -> {
            stringResource(R.string.settings_backup_invalid)
        }

        BackupMessage.NotRestored -> {
            stringResource(R.string.settings_backup_not_restored)
        }
    }

private const val JSON = "application/json"
