package io.github.kentprodan.mediatheq.feature.downloads

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.ui.AlbumRow
import io.github.kentprodan.mediatheq.core.ui.PlaylistRow
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.RemoveDownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.TrackRow
import io.github.kentprodan.mediatheq.core.ui.formatTotalDuration
import kotlin.time.Duration

@Composable
internal fun DownloadsRoute(
    onOpenAlbum: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadsScreen(state, viewModel::onAction, onOpenAlbum, onOpenPlaylist)
}

/**
 * The Downloads tab (#168): totals and free space, what is still downloading, then the finished downloads by
 * collection (albums, playlists) and the songs downloaded on their own.
 */
@Composable
internal fun DownloadsScreen(
    state: DownloadsUiState,
    onAction: (DownloadsAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit = {},
    onOpenPlaylist: (MediaId) -> Unit = {},
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
                    Content(state, onAction, onOpenAlbum, onOpenPlaylist)
                }
            }
        }
    }
}

@Composable
private fun Content(
    state: DownloadsUiState.Content,
    onAction: (DownloadsAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = MediatheqSpacing.l)) {
        item(key = "totals") { Totals(state.totals) }
        section(R.string.downloads_section_downloading, state.downloading.isNotEmpty()) {
            items(state.downloading, key = { "downloading-${it.track.id}" }) { DownloadRow(it, onAction) }
        }
        section(R.string.downloads_section_albums, state.albums.isNotEmpty()) {
            items(state.albums, key = { "album-${it.album.id}" }) { downloaded ->
                val origin = DownloadOrigin.Album(downloaded.album.id)
                AlbumRow(
                    downloaded.album,
                    onClick = { onOpenAlbum(downloaded.album.id) },
                    details = sizeAndDuration(downloaded.bytes, downloaded.duration),
                    menuItems = { close -> CollectionMenuItems(downloaded.tracks, origin, onAction, close) },
                )
            }
        }
        section(R.string.downloads_section_playlists, state.playlists.isNotEmpty()) {
            items(state.playlists, key = { "playlist-${it.playlist.id}" }) { downloaded ->
                val origin = DownloadOrigin.Playlist(downloaded.playlist.id)
                PlaylistRow(
                    downloaded.playlist,
                    onClick = { onOpenPlaylist(downloaded.playlist.id) },
                    details = sizeAndDuration(downloaded.bytes, downloaded.duration),
                    menuItems = { close -> CollectionMenuItems(downloaded.tracks, origin, onAction, close) },
                )
            }
        }
        section(R.string.downloads_section_songs, state.songs.isNotEmpty()) {
            items(state.songs, key = { "song-${it.track.id}" }) { DownloadRow(it, onAction) }
        }
    }
}

/** A titled section, only when [shown]. */
private fun LazyListScope.section(
    @StringRes title: Int,
    shown: Boolean,
    rows: LazyListScope.() -> Unit,
) {
    if (!shown) return
    item(key = "title-$title") {
        Text(
            stringResource(title),
            Modifier
                .padding(start = MediatheqSpacing.l, end = MediatheqSpacing.l, top = MediatheqSpacing.l)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    rows()
}

/** "3.8 GB · 12 h 5 min", then a bar of the share of the device's space the downloads take. */
@Composable
private fun Totals(totals: DownloadTotals) {
    val context = LocalContext.current
    val free = Formatter.formatShortFileSize(context, totals.freeBytes)
    Column(Modifier.padding(horizontal = MediatheqSpacing.l)) {
        Text(
            sizeAndDuration(totals.bytes, totals.duration),
            style = MaterialTheme.typography.titleSmall,
        )
        val space = totals.bytes + totals.freeBytes
        LinearProgressIndicator(
            progress = { if (space > 0) totals.bytes.toFloat() / space else 0f },
            modifier = Modifier.fillMaxWidth().padding(vertical = MediatheqSpacing.s),
        )
        Text(
            stringResource(R.string.downloads_free_space, free),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun sizeAndDuration(
    bytes: Long,
    duration: Duration,
): String =
    stringResource(
        R.string.downloads_size_duration,
        Formatter.formatShortFileSize(LocalContext.current, bytes),
        formatTotalDuration(duration),
    )

@Composable
private fun ColumnScope.CollectionMenuItems(
    tracks: List<Track>,
    origin: DownloadOrigin,
    onAction: (DownloadsAction) -> Unit,
    close: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.downloads_play)) },
        leadingIcon = { Icon(MqIcons.PlayArrow, contentDescription = null) },
        onClick = {
            onAction(DownloadsAction.PlayCollection(tracks, shuffle = false))
            close()
        },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.downloads_shuffle)) },
        leadingIcon = { Icon(MqIcons.Shuffle, contentDescription = null) },
        onClick = {
            onAction(DownloadsAction.PlayCollection(tracks, shuffle = true))
            close()
        },
    )
    RemoveDownloadMenuItem(onClick = { onAction(DownloadsAction.RemoveCollection(origin)) }, close = close)
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
        showTrackNumber = false,
        showCover = true,
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
                    // The server's conversion (#68): the device cannot play the original, e.g. Dolby Atmos on a Pixel.
                    if (download.converted) {
                        { StatusText(stringResource(R.string.downloads_converted)) }
                    } else {
                        null
                    }
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
