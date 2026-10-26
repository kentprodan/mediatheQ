package io.github.kentprodan.mediatheq.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.AlbumCard
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.FavoriteMenuItem
import io.github.kentprodan.mediatheq.core.ui.NothingDownloaded
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.TrackRow

@Composable
internal fun HomeRoute(
    onOpenAlbum: (MediaId) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, viewModel::onAction, onOpenAlbum, onOpenSettings, onOpenDownloads)
}

/** Stateless Home tab: Shuffle all, then album rows and favorite songs; empty sections are left out. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HomeTopBar(onOpenSettings) },
        // The app shell already pads for the navigation bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                HomeUiState.Loading -> {
                    // A local read of a few milliseconds: nothing to show meanwhile.
                }

                HomeUiState.FirstSync -> {
                    MqEmptyState(
                        MqIcons.MusicNote,
                        stringResource(R.string.home_first_sync_title),
                        body = stringResource(R.string.home_first_sync_body),
                    )
                }

                HomeUiState.Empty -> {
                    MqEmptyState(
                        MqIcons.MusicNote,
                        stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        actionLabel = stringResource(R.string.home_sync_now),
                        onAction = { onAction(HomeAction.Sync) },
                    )
                }

                HomeUiState.NothingDownloaded -> {
                    NothingDownloaded(onOpenDownloads)
                }

                is HomeUiState.Error -> {
                    val offline = state.error == MqError.Network
                    MqEmptyState(
                        if (offline) MqIcons.CloudOff else MqIcons.MusicNote,
                        stringResource(if (offline) R.string.home_offline_title else R.string.home_error_title),
                        body = stringResource(if (offline) R.string.home_offline_body else R.string.home_error_body),
                        actionLabel = stringResource(R.string.home_retry),
                        onAction = { onAction(HomeAction.Sync) },
                    )
                }

                is HomeUiState.Content -> {
                    HomeContent(state, onAction, onOpenAlbum)
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.home_title)) },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(MqIcons.Settings, contentDescription = stringResource(R.string.home_settings))
            }
        },
    )
}

@Composable
private fun HomeContent(
    content: HomeUiState.Content,
    onAction: (HomeAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
) {
    val rows = listOf(content.recentlyPlayed, content.recentlyAdded, content.favoriteAlbums)
    val flying = remember(rows) { flyingCovers(rows) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = MediatheqSpacing.l)) {
        item(key = "shuffle") {
            FilledTonalButton(
                onClick = { onAction(HomeAction.ShuffleAll) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l),
            ) {
                Icon(MqIcons.Shuffle, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.home_shuffle_all))
            }
        }
        albumRow("played", R.string.home_recently_played, content.recentlyPlayed, flying[0], onOpenAlbum)
        albumRow("added", R.string.home_recently_added, content.recentlyAdded, flying[1], onOpenAlbum)
        albumRow("favorite-albums", R.string.home_favorite_albums, content.favoriteAlbums, flying[2], onOpenAlbum)
        if (content.favoriteTracks.isNotEmpty()) {
            sectionTitle("favorite-songs", R.string.home_favorite_songs)
            items(content.favoriteTracks, key = { "favorite-songs-${it.id}" }) { track ->
                TrackRow(
                    track,
                    onClick = { onAction(HomeAction.PlayFavorite(track)) },
                    showCover = true,
                    menuItems = { close ->
                        QueueMenuItems(
                            onPlayNext = { onAction(HomeAction.PlayNext(track)) },
                            onAddToQueue = { onAction(HomeAction.AddToQueue(track)) },
                            close = close,
                        )
                        FavoriteMenuItem(
                            track.userData.isFavorite,
                            { onAction(HomeAction.SetFavorite(track, it)) },
                            close,
                        )
                        DownloadMenuItem({ onAction(HomeAction.Download(track)) }, close)
                    },
                )
            }
        }
    }
}

/** A titled, horizontally scrolling row of [albums]; nothing when it is empty. Keys are text (saveable). */
private fun LazyListScope.albumRow(
    key: String,
    @StringRes title: Int,
    albums: List<Album>,
    flying: Set<MediaId>,
    onOpenAlbum: (MediaId) -> Unit,
) {
    if (albums.isEmpty()) return
    sectionTitle(key, title)
    item(key = "$key-row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = MediatheqSpacing.l),
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
        ) {
            items(albums, key = { it.id.toString() }) { album ->
                AlbumCard(
                    album,
                    onClick = { onOpenAlbum(album.id) },
                    modifier = Modifier.width(MediatheqSizes.albumCardInRow),
                    sharedCover = album.id in flying,
                )
            }
        }
    }
}

private fun LazyListScope.sectionTitle(
    key: String,
    @StringRes title: Int,
) {
    item(key = "$key-title") {
        Text(
            stringResource(title),
            Modifier
                .padding(
                    start = MediatheqSpacing.l,
                    end = MediatheqSpacing.l,
                    top = MediatheqSpacing.l,
                    bottom = MediatheqSpacing.s,
                ).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * For each row, the albums whose cover flies into the album page: an album can be in several rows, and only its first
 * card may carry the shared cover (two visible covers with one key break the transition).
 */
internal fun flyingCovers(rows: List<List<Album>>): List<Set<MediaId>> {
    val seen = mutableSetOf<MediaId>()
    return rows.map { row -> row.filter { seen.add(it.id) }.mapTo(HashSet()) { it.id } }
}
