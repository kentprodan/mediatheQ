package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.SyncRecord
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.ui.formatTimeAgo
import io.github.kentprodan.mediatheq.core.ui.formatTotalDuration
import io.github.kentprodan.mediatheq.core.ui.formatTrackCount
import kotlin.time.Duration

/** Settings -> Sync manager (#148) as a bottom sheet over the hub. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SyncManagerSheet(
    onDismiss: () -> Unit,
    viewModel: SyncManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SyncManagerContent(state, viewModel::onAction)
    }
}

/** The sheet's content: a card per account, then Sync all. Stateless. */
@Composable
fun SyncManagerContent(
    state: SyncManagerUiState,
    onAction: (SyncManagerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MediatheqSpacing.l)
            .padding(bottom = MediatheqSpacing.l),
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
    ) {
        Text(
            stringResource(R.string.settings_sync_manager),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        val content = state as? SyncManagerUiState.Content ?: return@Column
        content.cards.forEach { card -> SyncCardView(card, onRetry = { onAction(SyncManagerAction.Retry(card.id)) }) }
        Button(
            onClick = { onAction(SyncManagerAction.SyncAll) },
            modifier = Modifier.fillMaxWidth(),
            enabled = content.offline == null && !content.syncing,
        ) {
            Icon(MqIcons.Sync, contentDescription = null)
            Text(stringResource(R.string.settings_sync_all), Modifier.padding(start = MediatheqSpacing.s))
        }
        content.offline?.let {
            Text(
                stringResource(it.text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncCardView(
    card: SyncCard,
    onRetry: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.padding(MediatheqSpacing.m),
            verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MqIcons.Lan, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = MediatheqSpacing.m)) {
                    Text(card.serverName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_providers_account_line, card.userName, card.baseUrl),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRetry, enabled = card.connection != Connection.Checking) {
                    Icon(MqIcons.Refresh, stringResource(R.string.settings_sync_retry, card.serverName))
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
            ) {
                ConnectionChip(card.connection)
                ResultChip(card)
            }
            val running = card.running
            when {
                running != null -> {
                    Text(
                        stringResource(
                            R.string.settings_sync_progress,
                            stringResource(running.step.text),
                            running.fetched,
                            running.total,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val indicator = Modifier.fillMaxWidth()
                    if (running.total > 0) {
                        LinearWavyProgressIndicator(
                            progress = { running.fetched.toFloat() / running.total },
                            modifier = indicator,
                        )
                    } else {
                        LinearWavyProgressIndicator(indicator)
                    }
                }

                card.last != null && card.ago != null -> {
                    Text(
                        lastSyncLine(card.last, card.ago),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!card.active) {
                Text(
                    stringResource(R.string.settings_sync_when_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "5 minutes ago · 3120 tracks in 24 seconds"; a failure has no count. */
@Composable
private fun lastSyncLine(
    record: SyncRecord,
    ago: Duration,
): String {
    val time = formatTimeAgo(ago)
    if (record.error != null) return time
    val seconds = record.duration.inWholeSeconds.toInt()
    val span =
        if (seconds < 60) {
            pluralStringResource(R.plurals.settings_sync_seconds, seconds, seconds)
        } else {
            formatTotalDuration(record.duration)
        }
    return stringResource(R.string.settings_sync_last_line, time, formatTrackCount(record.trackCount), span)
}

@Composable
private fun ConnectionChip(connection: Connection) {
    val colors = MaterialTheme.colorScheme
    when (connection) {
        Connection.Checking -> {
            StatusChip(
                stringResource(R.string.settings_sync_checking),
                colors.surfaceContainerHighest,
            )
        }

        Connection.Online -> {
            StatusChip(stringResource(R.string.settings_sync_online), colors.secondaryContainer)
        }

        Connection.Offline -> {
            StatusChip(stringResource(R.string.settings_sync_offline), colors.errorContainer)
        }
    }
}

@Composable
private fun ResultChip(card: SyncCard) {
    val colors = MaterialTheme.colorScheme
    val last = card.last
    when {
        card.running != null -> {
            StatusChip(stringResource(R.string.settings_sync_running), colors.tertiaryContainer)
        }

        last == null -> {
            StatusChip(stringResource(R.string.settings_sync_never), colors.surfaceContainerHighest)
        }

        last.error == null -> {
            StatusChip(stringResource(R.string.settings_sync_successful), colors.secondaryContainer)
        }

        else -> {
            val error = checkNotNull(last.error)
            val code = (error as? MqError.Server)?.code ?: (error as? MqError.Client)?.code ?: 0
            val reason = stringResource(error.text, code)
            StatusChip(stringResource(R.string.settings_sync_failed, reason), colors.errorContainer)
        }
    }
}

/** A status label in a full-round tonal pill: information, not a control. */
@Composable
private fun StatusChip(
    text: String,
    container: Color,
) {
    Surface(shape = CircleShape, color = container) {
        Text(
            text,
            Modifier.padding(horizontal = MediatheqSpacing.m, vertical = MediatheqSpacing.xs),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private val OfflineReason.text: Int
    get() =
        when (this) {
            OfflineReason.NoNetwork -> R.string.settings_sync_reason_no_network
            OfflineReason.OfflineMode -> R.string.settings_sync_reason_offline_mode
            OfflineReason.Unreachable -> R.string.settings_sync_reason_unreachable
        }

private val SyncStep.text: Int
    get() =
        when (this) {
            SyncStep.AlbumArtists -> R.string.settings_sync_step_album_artists
            SyncStep.Albums -> R.string.settings_sync_step_albums
            SyncStep.Tracks -> R.string.settings_sync_step_tracks
            SyncStep.Playlists -> R.string.settings_sync_step_playlists
            SyncStep.PlaylistEntries -> R.string.settings_sync_step_playlist_entries
        }

/** The error in words; Server and Client take their HTTP code as the argument. */
private val MqError.text: Int
    get() =
        when (this) {
            MqError.Network -> R.string.settings_sync_error_network
            MqError.Unauthorized -> R.string.settings_sync_error_unauthorized
            MqError.Forbidden -> R.string.settings_sync_error_forbidden
            MqError.NotFound -> R.string.settings_sync_error_not_found
            is MqError.Server -> R.string.settings_sync_error_server
            is MqError.Client -> R.string.settings_sync_error_client
            MqError.Storage -> R.string.settings_sync_error_storage
            is MqError.Unknown -> R.string.settings_sync_error_unknown
        }
