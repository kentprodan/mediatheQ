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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.AutoDownloads
import io.github.kentprodan.mediatheq.core.data.CleanupPreview
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqTabButtons
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
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
import kotlinx.coroutines.flow.drop
import kotlin.time.Duration

@Composable
internal fun DownloadsRoute(
    onOpenAlbum: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
    onOpenRules: () -> Unit,
    onBack: (() -> Unit)?,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(DownloadsTab.Files) }
    DownloadsScreen(state, viewModel::onAction, tab, { tab = it }, onOpenAlbum, onOpenPlaylist, onOpenRules, onBack)
}

/**
 * Manage offline files (#149): tabs Offline files (summary, Cleanup, filter, the finished downloads by collection and
 * the songs downloaded on their own, #168), Download queue and Auto offline rules.
 */
@Composable
internal fun DownloadsScreen(
    state: DownloadsUiState,
    onAction: (DownloadsAction) -> Unit,
    tab: DownloadsTab = DownloadsTab.Files,
    onTab: (DownloadsTab) -> Unit = {},
    onOpenAlbum: (MediaId) -> Unit = {},
    onOpenPlaylist: (MediaId) -> Unit = {},
    onOpenRules: () -> Unit = {},
    /** Set when Downloads opens over another page instead of being a navigation tab (#122). */
    onBack: (() -> Unit)? = null,
) {
    Surface(Modifier.fillMaxSize()) {
        Column {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.downloads_back))
                        }
                    }
                },
            )
            MqTabButtons(
                labels = DownloadsTab.entries.map { stringResource(it.label) },
                selected = tab.ordinal,
                onSelect = { onTab(DownloadsTab.entries[it]) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l),
            )
            when (state) {
                DownloadsUiState.Loading -> {
                    // A local read of a few milliseconds: nothing to show meanwhile.
                    Box(Modifier.fillMaxSize())
                }

                is DownloadsUiState.Content -> {
                    when (tab) {
                        DownloadsTab.Files -> FilesTab(state, onAction, onOpenAlbum, onOpenPlaylist)
                        DownloadsTab.Queue -> QueueTab(state, onAction)
                        DownloadsTab.Rules -> RulesTab(state.rules, onOpenRules)
                    }
                    state.cleanup?.let { CleanupDialog(it, onAction) }
                }
            }
        }
    }
}

private val DownloadsTab.label: Int
    get() =
        when (this) {
            DownloadsTab.Files -> R.string.downloads_tab_files
            DownloadsTab.Queue -> R.string.downloads_tab_queue
            DownloadsTab.Rules -> R.string.downloads_tab_rules
        }

@Composable
private fun FilesTab(
    state: DownloadsUiState.Content,
    onAction: (DownloadsAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
) {
    if (!state.hasFiles && state.unavailableOn == null) {
        Column {
            Summary(state.totals, onCleanup = { onAction(DownloadsAction.AskCleanup) })
            MqEmptyState(
                MqIcons.Download,
                stringResource(R.string.downloads_empty_title),
                body = stringResource(R.string.downloads_empty_body),
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = MediatheqSpacing.l).plusBottomBars()) {
        state.unavailableOn?.let { name -> item(key = "unavailable") { UnavailableNotice(name) } }
        item(key = "summary") { Summary(state.totals, onCleanup = { onAction(DownloadsAction.AskCleanup) }) }
        item(key = "filter") {
            // The field owns its text and cursor; the state answers a frame late and would move the cursor (#186).
            val field = rememberTextFieldState(state.filter)
            LaunchedEffect(field) {
                snapshotFlow { field.text.toString() }.drop(1).collect { onAction(DownloadsAction.Filter(it)) }
            }
            OutlinedTextField(
                field,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
                label = { Text(stringResource(R.string.downloads_filter)) },
                leadingIcon = { Icon(MqIcons.Search, contentDescription = null) },
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        }
        if (state.filter.isNotBlank() && state.albums.isEmpty() && state.playlists.isEmpty() && state.songs.isEmpty()) {
            item(key = "no-matches") {
                Text(
                    stringResource(R.string.downloads_no_matches),
                    Modifier.padding(MediatheqSpacing.l),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        section(R.string.downloads_section_albums, state.albums.isNotEmpty()) {
            items(state.albums, key = { "album-${it.album.id}" }) { downloaded ->
                val origin = DownloadOrigin.Album(downloaded.album.id)
                AlbumRow(
                    downloaded.album,
                    onClick = { onOpenAlbum(downloaded.album.id) },
                    details = autoMark(downloaded.auto, sizeAndDuration(downloaded.bytes, downloaded.duration)),
                    menuItems = { close ->
                        CollectionMenuItems(downloaded.tracks, origin.takeUnless { downloaded.auto }, onAction, close)
                    },
                )
            }
        }
        section(R.string.downloads_section_playlists, state.playlists.isNotEmpty()) {
            items(state.playlists, key = { "playlist-${it.playlist.id}" }) { downloaded ->
                val origin = DownloadOrigin.Playlist(downloaded.playlist.id)
                // A playlist gone from the library (deleted on the server) keeps a plain name.
                val name = downloaded.playlist.name.ifBlank { stringResource(R.string.downloads_playlist) }
                PlaylistRow(
                    downloaded.playlist.copy(name = name),
                    onClick = { onOpenPlaylist(downloaded.playlist.id) },
                    details = autoMark(downloaded.auto, sizeAndDuration(downloaded.bytes, downloaded.duration)),
                    menuItems = { close ->
                        CollectionMenuItems(downloaded.tracks, origin.takeUnless { downloaded.auto }, onAction, close)
                    },
                )
            }
        }
        section(R.string.downloads_section_songs, state.songs.isNotEmpty()) {
            items(
                state.songs,
                key = { "song-${it.track.id}" },
            ) { DownloadRow(it, state.paused, onAction, DownloadsAction::RemoveSong) }
        }
    }
}

@Composable
private fun QueueTab(
    state: DownloadsUiState.Content,
    onAction: (DownloadsAction) -> Unit,
) {
    if (state.downloading.isEmpty()) {
        MqEmptyState(
            MqIcons.Download,
            stringResource(R.string.downloads_queue_empty_title),
            body = stringResource(R.string.downloads_queue_empty_body),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = MediatheqSpacing.l).plusBottomBars()) {
        item(key = "pause") {
            FilledTonalButton(
                onClick = { onAction(if (state.paused) DownloadsAction.ResumeAll else DownloadsAction.PauseAll) },
                modifier =
                    Modifier.fillMaxWidth().padding(
                        horizontal = MediatheqSpacing.l,
                        vertical = MediatheqSpacing.s,
                    ),
            ) {
                Icon(if (state.paused) MqIcons.PlayArrow else MqIcons.PauseFilled, contentDescription = null)
                Text(
                    stringResource(if (state.paused) R.string.downloads_resume_all else R.string.downloads_pause_all),
                    Modifier.padding(start = MediatheqSpacing.s),
                )
            }
        }
        items(state.downloading, key = { "queue-${it.track.id}" }) {
            DownloadRow(it, state.paused, onAction, DownloadsAction::Remove)
        }
    }
}

/** The automatic downloads of favorites as set in Settings -> Media (#145), and the way there. */
@Composable
private fun RulesTab(
    rules: AutoRules,
    onOpenRules: () -> Unit,
) {
    if (rules.option == AutoDownloads.Disabled) {
        MqEmptyState(
            MqIcons.Favorite,
            stringResource(R.string.downloads_rules_empty_title),
            body = stringResource(R.string.downloads_rules_empty_body),
            actionLabel = stringResource(R.string.downloads_rules_change),
            onAction = onOpenRules,
        )
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = MediatheqSpacing.s),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.downloads_rules_favorites)) },
            supportingContent = { Text(stringResource(rules.option.label)) },
            leadingContent = { Icon(MqIcons.Favorite, contentDescription = null) },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.downloads_rules_remove_unfavorited)) },
            supportingContent = {
                Text(
                    stringResource(
                        if (rules.removeUnfavorited) R.string.downloads_rules_on else R.string.downloads_rules_off,
                    ),
                )
            },
            leadingContent = { Icon(MqIcons.Delete, contentDescription = null) },
        )
        FilledTonalButton(
            onClick = onOpenRules,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        ) { Text(stringResource(R.string.downloads_rules_change)) }
    }
}

private val AutoDownloads.label: Int
    get() =
        when (this) {
            AutoDownloads.Disabled -> R.string.downloads_rules_disabled
            AutoDownloads.Tracks -> R.string.downloads_rules_tracks
            AutoDownloads.TracksAndAlbums -> R.string.downloads_rules_tracks_albums
            AutoDownloads.TracksAlbumsAndPlaylists -> R.string.downloads_rules_tracks_albums_playlists
        }

/** Cleanup asks first, saying what goes: failed downloads and leftover data, never what is complete. */
@Composable
private fun CleanupDialog(
    preview: CleanupPreview,
    onAction: (DownloadsAction) -> Unit,
) {
    MqDialog(
        title = stringResource(R.string.downloads_cleanup_title),
        confirmLabel =
            stringResource(R.string.downloads_cleanup_confirm).takeIf {
                preview.failed > 0 ||
                    preview.bytes > 0
            },
        onConfirm = { onAction(DownloadsAction.ConfirmCleanup) },
        dismissLabel = stringResource(R.string.downloads_cancel),
        onDismiss = { onAction(DownloadsAction.DismissCleanup) },
    ) { CleanupBody(preview) }
}

/** The dialog's text, also drawn in place by the screenshot tests. */
@Composable
internal fun CleanupBody(preview: CleanupPreview) {
    val text =
        if (preview.failed == 0 && preview.bytes == 0L) {
            stringResource(R.string.downloads_cleanup_nothing)
        } else {
            stringResource(
                R.string.downloads_cleanup_body,
                pluralStringResource(R.plurals.downloads_failed_count, preview.failed, preview.failed),
                Formatter.formatShortFileSize(LocalContext.current, preview.bytes),
            )
        }
    Text(text, style = MaterialTheme.typography.bodyMedium)
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

/** The downloads' SD card is missing (#146): why nothing plays offline, and that nothing was deleted. */
@Composable
private fun UnavailableNotice(name: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
    ) {
        Text(
            stringResource(R.string.downloads_unavailable, name.ifEmpty { stringResource(R.string.downloads_sd_card) }),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(MediatheqSpacing.l),
        )
    }
}

/**
 * The summary card (#149): manual and automatic downloads, the total ("3.8 GB · 12 h 5 min"), a bar of the share of the
 * device's space the downloads take, free space, and Cleanup.
 */
@Composable
private fun Summary(
    totals: DownloadTotals,
    onCleanup: () -> Unit,
) {
    val context = LocalContext.current
    val free = Formatter.formatShortFileSize(context, totals.freeBytes)
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(MediatheqSpacing.l)) {
            UsageLine(R.string.downloads_summary_manual, totals.manual)
            UsageLine(R.string.downloads_summary_auto, totals.auto)
            Text(
                stringResource(R.string.downloads_summary_total, sizeAndDuration(totals.bytes, totals.duration)),
                Modifier.padding(top = MediatheqSpacing.s),
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
            OutlinedButton(onClick = onCleanup, modifier = Modifier.padding(top = MediatheqSpacing.s)) {
                Icon(MqIcons.Delete, contentDescription = null)
                Text(stringResource(R.string.downloads_cleanup), Modifier.padding(start = MediatheqSpacing.s))
            }
        }
    }
}

/** "Manual downloads: 12 tracks · 1.2 GB". */
@Composable
private fun UsageLine(
    @StringRes label: Int,
    usage: OfflineUsage,
) {
    Text(
        stringResource(
            label,
            pluralStringResource(R.plurals.downloads_track_count, usage.tracks, usage.tracks),
            Formatter.formatShortFileSize(LocalContext.current, usage.bytes),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
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

/** "Auto · [details]" for a favorite's automatic download (#145). */
@Composable
private fun autoMark(
    auto: Boolean,
    details: String,
): String = if (auto) stringResource(R.string.downloads_auto_details, details) else details

/** [origin] `null`: an automatic download, removed by unfavoriting, not here (#145). */
@Composable
private fun ColumnScope.CollectionMenuItems(
    tracks: List<Track>,
    origin: DownloadOrigin?,
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
    if (origin != null) {
        RemoveDownloadMenuItem(onClick = { onAction(DownloadsAction.RemoveCollection(origin)) }, close = close)
    }
}

@Composable
private fun DownloadRow(
    download: TrackDownload,
    paused: Boolean,
    onAction: (DownloadsAction) -> Unit,
    remove: (Track) -> DownloadsAction,
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
            // A favorite's automatic download goes by unfavoriting it (#145).
            if (download.removable) RemoveDownloadMenuItem(onClick = { onAction(remove(track)) }, close = close)
        },
        supportingContent =
            when (val state = download.state) {
                DownloadState.Completed -> {
                    // The server's conversion (#68): the device cannot play the original, e.g. Dolby Atmos on a Pixel.
                    val notes =
                        listOfNotNull(
                            stringResource(R.string.downloads_auto).takeIf { download.autoOnly },
                            stringResource(R.string.downloads_converted).takeIf { download.converted },
                        )
                    if (notes.isNotEmpty()) {
                        { StatusText(notes.joinToString(" · ")) }
                    } else {
                        null
                    }
                }

                else -> {
                    { QueueStatusText(state, queueStatus(state, paused)) }
                }
            },
    )
}

/** Only kept as a favorite's automatic download (#145): every origin is automatic. */
private val TrackDownload.autoOnly: Boolean
    get() = origins.isNotEmpty() && origins.all { it.auto }

/**
 * Remove does something here: a download under way goes whatever its origin; a finished song listed as a favorite
 * (#145) goes only when it was also downloaded on its own (Remove takes that away).
 */
private val TrackDownload.removable: Boolean
    get() =
        state != DownloadState.Completed ||
            DownloadOrigin.Single in origins ||
            (origins.isEmpty())

@Composable
private fun QueueStatusText(
    state: DownloadState,
    status: QueueStatus,
) {
    when (status) {
        QueueStatus.Downloading -> DownloadProgress((state as? DownloadState.Downloading)?.progress)
        QueueStatus.Queued -> StatusText(stringResource(R.string.downloads_queued))
        QueueStatus.WaitingForWifi -> StatusText(stringResource(R.string.downloads_waiting_for_wifi))
        QueueStatus.Paused -> StatusText(stringResource(R.string.downloads_paused))
        QueueStatus.Failed -> StatusText(stringResource(R.string.downloads_failed), MaterialTheme.colorScheme.error)
    }
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
