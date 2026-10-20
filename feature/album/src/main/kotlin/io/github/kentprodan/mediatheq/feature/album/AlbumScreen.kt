package io.github.kentprodan.mediatheq.feature.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqPlayShuffleButtons
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.motion.mqSharedCover
import io.github.kentprodan.mediatheq.core.designsystem.motion.sharedCoverKey
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.DiscHeader
import io.github.kentprodan.mediatheq.core.ui.OverflowMenu
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.TrackRow
import io.github.kentprodan.mediatheq.core.ui.formatTotalDuration
import io.github.kentprodan.mediatheq.core.ui.formatTrackCount
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
                actions = {
                    if (album != null && (state as AlbumUiState.Content).tracks.isNotEmpty()) {
                        OverflowMenu(stringResource(R.string.album_more_options, album.title)) { close ->
                            QueueMenuItems(
                                onPlayNext = { onAction(AlbumAction.PlayNext()) },
                                onAddToQueue = { onAction(AlbumAction.AddToQueue()) },
                                close = close,
                            )
                        }
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
                    item(key = "buttons") {
                        MqPlayShuffleButtons(
                            enabled = state.tracks.isNotEmpty(),
                            onPlay = { onAction(AlbumAction.Play) },
                            onShuffle = { onAction(AlbumAction.Shuffle) },
                        )
                    }
                    state.discs.forEach { disc ->
                        if (state.showDiscHeaders) item(key = "disc-${disc.number}") { DiscHeader(disc.number) }
                        items(disc.tracks, key = { it.id.toString() }) { track ->
                            TrackRow(
                                track,
                                onClick = { onAction(AlbumAction.PlayTrack(track.id)) },
                                isCurrent = track.id == state.currentTrackId,
                                menuItems = { close ->
                                    QueueMenuItems(
                                        onPlayNext = { onAction(AlbumAction.PlayNext(track.id)) },
                                        onAddToQueue = { onAction(AlbumAction.AddToQueue(track.id)) },
                                        close = close,
                                    )
                                },
                            )
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

/** "2016 · 11 tracks · 45 min". */
@Composable
private fun albumFacts(
    album: Album,
    trackCount: Int,
    duration: Duration,
): String =
    listOfNotNull(
        album.year?.toString(),
        formatTrackCount(trackCount),
        formatTotalDuration(duration),
    ).joinToString(" · ")

private const val COVER_WIDTH_FRACTION = 0.6f
