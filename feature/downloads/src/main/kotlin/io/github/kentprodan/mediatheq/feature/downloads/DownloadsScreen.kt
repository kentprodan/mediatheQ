package io.github.kentprodan.mediatheq.feature.downloads

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.RemoveDownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.TrackRow

@Composable
internal fun DownloadsRoute(viewModel: DownloadsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadsScreen(state, viewModel::onAction)
}

/** The Downloads tab: storage used, then the downloaded tracks grouped by album, each with its state. */
@Composable
internal fun DownloadsScreen(
    state: DownloadsUiState,
    onAction: (DownloadsAction) -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column {
            TopAppBar(title = { Text(stringResource(R.string.downloads_title)) })
            when (state) {
                DownloadsUiState.Loading -> {
                    // A local read of a few milliseconds: nothing to show meanwhile.
                    Box(Modifier.fillMaxSize())
                }

                DownloadsUiState.Empty -> {
                    MqEmptyState(
                        MqIcons.Download,
                        stringResource(R.string.downloads_empty_title),
                        body = stringResource(R.string.downloads_empty_body),
                    )
                }

                is DownloadsUiState.Content -> {
                    Content(state, onAction)
                }
            }
        }
    }
}

@Composable
private fun Content(
    state: DownloadsUiState.Content,
    onAction: (DownloadsAction) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = MediatheqSpacing.l)) {
        item(key = "storage") {
            Text(
                stringResource(
                    R.string.downloads_storage_used,
                    Formatter.formatShortFileSize(context, state.storageUsed),
                ),
                Modifier.padding(horizontal = MediatheqSpacing.l),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.albums.forEachIndexed { index, album ->
            item(key = "album-$index-${album.title}") {
                Text(
                    album.title ?: stringResource(R.string.downloads_unknown_album),
                    Modifier
                        .padding(start = MediatheqSpacing.l, end = MediatheqSpacing.l, top = MediatheqSpacing.l)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            items(album.downloads, key = { it.track.id.toString() }) { DownloadRow(it, onAction) }
        }
    }
}

@Composable
private fun DownloadRow(
    download: TrackDownload,
    onAction: (DownloadsAction) -> Unit,
) {
    val track = download.track
    TrackRow(
        track = track,
        onClick = { onAction(DownloadsAction.Play(track)) },
        menuItems = { close ->
            QueueMenuItems(
                onPlayNext = { onAction(DownloadsAction.PlayNext(track)) },
                onAddToQueue = { onAction(DownloadsAction.AddToQueue(track)) },
                close = close,
            )
            if (download.state == DownloadState.Failed) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.downloads_retry)) },
                    leadingIcon = { Icon(MqIcons.Download, contentDescription = null) },
                    onClick = {
                        onAction(DownloadsAction.Retry(track))
                        close()
                    },
                )
            }
            RemoveDownloadMenuItem(onClick = { onAction(DownloadsAction.Remove(track)) }, close = close)
        },
        supportingContent =
            when (val state = download.state) {
                DownloadState.Completed -> {
                    null
                }

                is DownloadState.Downloading -> {
                    { DownloadProgress(state.progress) }
                }

                DownloadState.Queued -> {
                    { StatusText(stringResource(R.string.downloads_queued)) }
                }

                DownloadState.WaitingForWifi -> {
                    { StatusText(stringResource(R.string.downloads_waiting_for_wifi)) }
                }

                DownloadState.Failed -> {
                    { StatusText(stringResource(R.string.downloads_failed), MaterialTheme.colorScheme.error) }
                }
            },
    )
}

@Composable
private fun DownloadProgress(progress: Float?) {
    Column {
        StatusText(
            if (progress == null) {
                stringResource(R.string.downloads_downloading)
            } else {
                stringResource(R.string.downloads_downloading_percent, (progress * 100).toInt())
            },
        )
        val indicatorModifier = Modifier.fillMaxWidth().padding(top = MediatheqSpacing.xs)
        if (progress == null) {
            LinearWavyProgressIndicator(indicatorModifier)
        } else {
            LinearWavyProgressIndicator(progress = { progress }, modifier = indicatorModifier)
        }
    }
}

@Composable
private fun StatusText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(text, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
