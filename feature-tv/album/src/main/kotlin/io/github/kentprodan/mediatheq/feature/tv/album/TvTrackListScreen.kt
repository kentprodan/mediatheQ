package io.github.kentprodan.mediatheq.feature.tv.album

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.common.formatDuration
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMessage
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvCover
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvFormatBadge
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvTrackRow

@Composable
fun TvAlbumRoute(albumId: MediaId) {
    val viewModel =
        hiltViewModel<TvAlbumViewModel, TvAlbumViewModel.Factory>(key = albumId.toString()) { it.create(albumId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvTrackListScreen(state, viewModel::onAction)
}

@Composable
fun TvPlaylistRoute(playlistId: MediaId) {
    val viewModel =
        hiltViewModel<TvPlaylistViewModel, TvPlaylistViewModel.Factory>(key = playlistId.toString()) {
            it.create(playlistId)
        }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvTrackListScreen(state, viewModel::onAction, showNumbers = false)
}

/**
 * An album or playlist page on TV (#238): the cover and what it is, Play / Shuffle (Resume) / Favorite, then the
 * tracks (by disc when there are several). Focus starts on Play; Down goes into the list. Select on a track plays the
 * list from it; long-press (or the menu key) opens Play next, Add to queue, Favorite. [showNumbers] is off for a
 * playlist, whose tracks keep their album numbers.
 */
@Composable
fun TvTrackListScreen(
    state: TvTrackListUiState,
    onAction: (TvTrackListAction) -> Unit,
    modifier: Modifier = Modifier,
    showNumbers: Boolean = true,
) {
    when (state) {
        TvTrackListUiState.Loading -> {
            Box(modifier.fillMaxSize())
        }

        TvTrackListUiState.NotFound -> {
            MqTvMessage(
                stringResource(R.string.tv_album_not_found_title),
                stringResource(R.string.tv_album_not_found_body),
                modifier,
            )
        }

        is TvTrackListUiState.Content -> {
            Content(state, onAction, modifier, showNumbers)
        }
    }
}

@Composable
private fun Content(
    state: TvTrackListUiState.Content,
    onAction: (TvTrackListAction) -> Unit,
    modifier: Modifier,
    showNumbers: Boolean,
) {
    val play = remember { FocusRequester() }
    var menu by remember { mutableStateOf<Track?>(null) }
    val severalDiscs = state.discs.size > 1
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = EDGE, end = EDGE, top = 32.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header") { Header(state, onAction, Modifier.padding(bottom = 24.dp), play) }
        if (state.tracks.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.tv_album_no_tracks), style = MaterialTheme.typography.bodyLarge)
            }
        }
        var index = 0
        state.discs.forEach { disc ->
            if (severalDiscs) {
                item(key = "disc ${disc.number}") {
                    Text(
                        stringResource(R.string.tv_album_disc, disc.number),
                        Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            val first = index
            // By position: a playlist can hold the same track twice.
            itemsIndexed(disc.tracks, key = { i, _ -> "track ${first + i}" }) { i, track ->
                val position = first + i
                MqTvTrackRow(
                    track,
                    onClick = { onAction(TvTrackListAction.PlayTrack(position)) },
                    Modifier.onPreviewKeyEvent { event ->
                        // The remote's menu key does what a long-press does.
                        val isMenu = event.key == Key.Menu
                        if (isMenu && event.type == KeyEventType.KeyUp) menu = track
                        isMenu
                    },
                    number = if (showNumbers) track.trackNumber else position + 1,
                    isPlaying = track.id == state.currentTrackId,
                    onLongClick = { menu = track },
                )
            }
            index += disc.tracks.size
        }
    }
    LaunchedEffect(Unit) {
        // The header is a lazy item, laid out after this effect starts.
        withFrameNanos {}
        runCatching { play.requestFocus() }
    }
    menu?.let { track -> TrackMenu(track, onAction) { menu = null } }
}

@Composable
private fun Header(
    state: TvTrackListUiState.Content,
    onAction: (TvTrackListAction) -> Unit,
    modifier: Modifier,
    play: FocusRequester,
) {
    val header = state.header
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Bottom) {
        MqTvCover(header.artwork, MqTvIcons.MusicNote, Modifier.size(COVER).clip(MaterialTheme.shapes.medium))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                header.title,
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            header.subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 1) }
            val tracks = pluralStringResource(R.plurals.tv_album_tracks, header.trackCount, header.trackCount)
            Text(
                listOfNotNull(header.year?.toString(), tracks, formatDuration(header.duration)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            header.quality?.let { MqTvFormatBadge(it) }
            // Up from the tracks comes back to the button focused last, the first one the first time (#350).
            val others = remember { List(3) { FocusRequester() } }
            var last by remember { mutableStateOf(play) }

            fun Modifier.remembered(requester: FocusRequester) =
                focusRequester(requester).onFocusChanged { if (it.isFocused) last = requester }
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .focusProperties {
                        onEnter =
                            { if (requestedFocusDirection == FocusDirection.Up) last.requestFocus() }
                    }.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val canPlay = state.tracks.isNotEmpty()
                MqTvButton(
                    stringResource(R.string.tv_album_play),
                    { onAction(TvTrackListAction.Play) },
                    if (canPlay) Modifier.remembered(play) else Modifier,
                    enabled = canPlay,
                    icon = MqTvIcons.Play,
                )
                if (state.resume != null) {
                    MqTvButton(
                        stringResource(R.string.tv_album_resume),
                        { onAction(TvTrackListAction.Resume) },
                        Modifier.remembered(others[0]),
                        icon = MqTvIcons.Resume,
                    )
                }
                MqTvButton(
                    stringResource(R.string.tv_album_shuffle),
                    { onAction(TvTrackListAction.Shuffle) },
                    if (canPlay) Modifier.remembered(others[1]) else Modifier,
                    enabled = canPlay,
                    icon = MqTvIcons.Shuffle,
                )
                MqTvButton(
                    stringResource(if (header.isFavorite) R.string.tv_album_favorited else R.string.tv_album_favorite),
                    { onAction(TvTrackListAction.ToggleFavorite) },
                    // Nothing to play (an empty playlist): the first focus goes here, a disabled button takes none.
                    Modifier.remembered(if (canPlay) others[2] else play),
                    icon = if (header.isFavorite) MqTvIcons.FavoriteFilled else MqTvIcons.Favorite,
                )
            }
        }
    }
}

@Composable
private fun TrackMenu(
    track: Track,
    onAction: (TvTrackListAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val favorite = track.userData.isFavorite
    MqTvMenu(
        track.title,
        listOf(
            MqTvMenuEntry(stringResource(R.string.tv_album_play_next), MqTvIcons.PlayNext) {
                onAction(TvTrackListAction.PlayNext(track))
            },
            MqTvMenuEntry(stringResource(R.string.tv_album_add_to_queue), MqTvIcons.Add) {
                onAction(TvTrackListAction.AddToQueue(track))
            },
            MqTvMenuEntry(
                stringResource(if (favorite) R.string.tv_album_remove_favorite else R.string.tv_album_add_favorite),
                if (favorite) MqTvIcons.FavoriteFilled else MqTvIcons.Favorite,
            ) { onAction(TvTrackListAction.ToggleTrackFavorite(track)) },
        ),
        onDismiss,
    )
}

private val EDGE = 48.dp
private val COVER = 200.dp
