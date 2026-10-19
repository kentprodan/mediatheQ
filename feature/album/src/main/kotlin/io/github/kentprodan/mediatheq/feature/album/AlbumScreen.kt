package io.github.kentprodan.mediatheq.feature.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.motion.mqSharedCover
import io.github.kentprodan.mediatheq.core.designsystem.motion.sharedCoverKey
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.DiscHeader
import io.github.kentprodan.mediatheq.core.ui.TrackRow
import kotlin.time.Duration

@Composable
internal fun AlbumRoute(
    albumId: MediaId,
    onBack: () -> Unit,
    viewModel: AlbumViewModel =
        hiltViewModel<AlbumViewModel, AlbumViewModel.Factory>(key = albumId.toString(), creationCallback = {
            it.create(albumId)
        }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlbumScreen(albumId, state, viewModel::onAction, onBack)
}

/** Stateless album page: collapsing top bar with title and artists, cover, facts, Play / Shuffle, tracks by disc. */
@Composable
fun AlbumScreen(
    albumId: MediaId,
    state: AlbumUiState,
    onAction: (AlbumAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val album = (state as? AlbumUiState.Content)?.album
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(album?.title.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                subtitle = {
                    if (album != null) {
                        Text(
                            album.albumArtists.joinToString { it.name },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.album_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        // The app shell already pads for the navigation bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when (state) {
            AlbumUiState.Loading -> {
                Unit
            }

            AlbumUiState.NotFound -> {
                MqEmptyState(
                    MqIcons.MusicNote,
                    stringResource(R.string.album_not_found_title),
                    Modifier.padding(padding),
                    body = stringResource(R.string.album_not_found_body),
                    actionLabel = stringResource(R.string.album_back),
                    onAction = onBack,
                )
            }

            is AlbumUiState.Content -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = MediatheqSpacing.l),
                ) {
                    item(key = "header") { AlbumHeader(albumId, state) }
                    item(key = "buttons") { PlayButtons(enabled = state.tracks.isNotEmpty(), onAction) }
                    state.discs.forEach { disc ->
                        if (state.showDiscHeaders) item(key = "disc-${disc.number}") { DiscHeader(disc.number) }
                        items(disc.tracks, key = { it.id.toString() }) { track ->
                            // shortcut: the track menu comes with the queue and playlist actions (#14, #16).
                            TrackRow(track, onClick = { onAction(AlbumAction.PlayTrack(track.id)) }, onMoreClick = {})
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    albumId: MediaId,
    state: AlbumUiState.Content,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
    ) {
        MqCover(
            model = state.album.artwork,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(COVER_WIDTH_FRACTION).mqSharedCover(sharedCoverKey(albumId)),
            blurHash = state.album.artwork?.blurHash,
            shape = MaterialTheme.shapes.extraLarge,
        )
        Text(
            albumFacts(state.album, state.trackCount, state.duration),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Play (filled) and Shuffle (tonal), side by side (expressive-design component map). */
@Composable
private fun PlayButtons(
    enabled: Boolean,
    onAction: (AlbumAction) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
    ) {
        Button(onClick = { onAction(AlbumAction.Play) }, Modifier.weight(1f), enabled = enabled) {
            Icon(MqIcons.PlayArrow, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Text(
                stringResource(R.string.album_play),
                Modifier.padding(start = ButtonDefaults.IconSpacing),
                maxLines = 1,
            )
        }
        FilledTonalButton(onClick = { onAction(AlbumAction.Shuffle) }, Modifier.weight(1f), enabled = enabled) {
            Icon(MqIcons.Shuffle, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Text(
                stringResource(R.string.album_shuffle),
                Modifier.padding(start = ButtonDefaults.IconSpacing),
                maxLines = 1,
            )
        }
    }
}

/** "2016 · 11 tracks · 45 min". */
@Composable
private fun albumFacts(
    album: Album,
    trackCount: Int,
    duration: Duration,
): String {
    val minutes = duration.inWholeMinutes.toInt()
    val length =
        if (minutes >= 60) {
            stringResource(R.string.album_duration_hours, minutes / 60, minutes % 60)
        } else {
            stringResource(R.string.album_duration_minutes, minutes)
        }
    val tracks = pluralStringResource(R.plurals.album_track_count, trackCount, trackCount)
    return listOfNotNull(album.year?.toString(), tracks, length).joinToString(" · ")
}

private const val COVER_WIDTH_FRACTION = 0.6f
