package io.github.kentprodan.mediatheq.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCarouselItem
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCoverCarousel
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.HomeDisplay
import io.github.kentprodan.mediatheq.core.model.HomeSectionSettings
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.ui.AlbumCard
import io.github.kentprodan.mediatheq.core.ui.ArtistCard
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.FavoriteMenuItem
import io.github.kentprodan.mediatheq.core.ui.LocalMediaListStyle
import io.github.kentprodan.mediatheq.core.ui.NothingDownloaded
import io.github.kentprodan.mediatheq.core.ui.PlaylistCard
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.TrackRow
import io.github.kentprodan.mediatheq.core.ui.title

/** Where Home opens things. */
data class HomeNavigation(
    val onOpenAlbum: (MediaId) -> Unit = {},
    val onOpenArtist: (MediaId) -> Unit = {},
    val onOpenPlaylist: (MediaId) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenDownloads: () -> Unit = {},
)

@Composable
internal fun HomeRoute(
    navigation: HomeNavigation,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, viewModel::onAction, navigation)
}

/** Stateless Home tab: Shuffle all, then the chosen sections (#127) in their order and display. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HomeTopBar(navigation.onOpenSettings) },
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
                    NothingDownloaded(navigation.onOpenDownloads)
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
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { onAction(HomeAction.Refresh) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        HomeContent(state, onAction, navigation)
                    }
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
    navigation: HomeNavigation,
) {
    val flying =
        remember(
            content.sections,
        ) {
            flyingCovers(
                content.sections.map {
                    (it.items as? HomeItems.Albums)
                        ?.albums
                        .takeUnless { _ ->
                            it.settings.display ==
                                HomeDisplay.Carousel
                        }.orEmpty()
                },
            )
        }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = MediatheqSpacing.l).plusBottomBars()) {
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
        content.sections.forEachIndexed { index, section ->
            section(section, flying[index], onAction, navigation)
        }
    }
}

/**
 * One section: its header, then its items in the section's display and settings (#182). Keys are text (saveable).
 * Also the preview of the section's settings page.
 */
internal fun LazyListScope.section(
    content: HomeSectionContent,
    flying: Set<MediaId>,
    onAction: (HomeAction) -> Unit,
    navigation: HomeNavigation,
) {
    val key = content.section.name
    val settings = content.settings
    if (settings.showHeader) {
        item(key = "$key-title") { SectionHeader(content) }
    }
    val items = content.items
    if (items is HomeItems.Shortcuts) {
        item(key = "$key-items") { MixShortcuts(items, onAction) }
        return
    }
    // Tracks have no cards: a track section always lists its rows.
    if (items is HomeItems.Tracks) {
        itemsIndexed(items.tracks, key = { _, track -> "$key-${track.id}" }) { index, track ->
            HomeTrackRow(
                track,
                onPlay = { onAction(HomeAction.PlayTracks(items.tracks, index)) },
                onAction,
                showArtists = !settings.hideSubtitles,
            )
        }
        return
    }
    item(key = "$key-items") {
        // The section's own text and overlay choices over Settings -> Media lists (#182).
        val style = LocalMediaListStyle.current
        CompositionLocalProvider(
            LocalMediaListStyle provides
                style.copy(
                    centerGridText = settings.centerText ?: style.centerGridText,
                    favoriteOverlay = settings.favoriteOverlay ?: style.favoriteOverlay,
                    offlineOverlay = settings.offlineOverlay ?: style.offlineOverlay,
                ),
        ) { SectionItems(items, settings, flying, onAction, navigation) }
    }
}

@Composable
private fun SectionItems(
    items: HomeItems,
    settings: HomeSectionSettings,
    flying: Set<MediaId>,
    onAction: (HomeAction) -> Unit,
    navigation: HomeNavigation,
) {
    run {
        when (settings.display) {
            HomeDisplay.Carousel -> {
                MqCoverCarousel(
                    carouselItems(items),
                    onClick = { open(it.key as MediaId, items, navigation) },
                )
            }

            HomeDisplay.Grid -> {
                CardRow(items, flying, navigation, settings, onAction, rows = settings.gridRows)
            }

            // A list of albums, artists or playlists is a row of cards (List is for tracks).
            HomeDisplay.Row, HomeDisplay.List -> {
                CardRow(items, flying, navigation, settings, onAction, rows = 1)
            }
        }
    }
}

@Composable
private fun SectionHeader(content: HomeSectionContent) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = MediatheqSpacing.l,
                end = MediatheqSpacing.l,
                top = MediatheqSpacing.l,
                bottom = MediatheqSpacing.s,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(content.section.title),
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        // The full list behind it comes with #183.
        if (content.settings.moreIndicator) {
            Icon(MqIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Cards scrolling sideways; with [rows] > 1 each column holds that many cards (a horizontal grid). */
@Composable
private fun CardRow(
    items: HomeItems,
    flying: Set<MediaId>,
    navigation: HomeNavigation,
    settings: HomeSectionSettings,
    onAction: (HomeAction) -> Unit,
    rows: Int,
) {
    val subtitles = !settings.hideSubtitles
    val cards: List<@Composable (Modifier) -> Unit> =
        when (items) {
            is HomeItems.Albums -> {
                items.albums.map { album ->
                    { modifier: Modifier ->
                        AlbumCard(
                            album,
                            { navigation.onOpenAlbum(album.id) },
                            modifier,
                            sharedCover = album.id in flying,
                            showSubtitle = subtitles,
                            onPlay = { onAction(HomeAction.PlayAlbum(album.id)) }.takeIf { settings.playButton },
                        )
                    }
                }
            }

            is HomeItems.Artists -> {
                items.artists.map { artist ->
                    { modifier: Modifier ->
                        ArtistCard(
                            artist,
                            { navigation.onOpenArtist(artist.id) },
                            modifier,
                            onPlay = { onAction(HomeAction.PlayArtist(artist.id)) }.takeIf { settings.playButton },
                        )
                    }
                }
            }

            is HomeItems.Playlists -> {
                items.playlists.map { playlist ->
                    { modifier: Modifier ->
                        PlaylistCard(
                            playlist,
                            { navigation.onOpenPlaylist(playlist.id) },
                            modifier,
                            showSubtitle = subtitles,
                            onPlay = { onAction(HomeAction.PlayPlaylist(playlist.id)) }.takeIf { settings.playButton },
                        )
                    }
                }
            }

            is HomeItems.Tracks, is HomeItems.Shortcuts -> {
                emptyList()
            }
        }
    LazyRow(
        contentPadding = PaddingValues(horizontal = MediatheqSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
    ) {
        items(cards.chunked(rows)) { column ->
            Column(verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
                column.forEach { card -> card(Modifier.width(MediatheqSizes.albumCardInRow)) }
            }
        }
    }
}

@Composable
private fun HomeTrackRow(
    track: Track,
    onPlay: () -> Unit,
    onAction: (HomeAction) -> Unit,
    showArtists: Boolean,
) {
    TrackRow(
        track,
        onClick = onPlay,
        showCover = true,
        showArtists = showArtists,
        menuItems = { close ->
            QueueMenuItems(
                onPlayNext = { onAction(HomeAction.PlayNext(track)) },
                onAddToQueue = { onAction(HomeAction.AddToQueue(track)) },
                close = close,
            )
            FavoriteMenuItem(track.userData.isFavorite, { onAction(HomeAction.SetFavorite(track, it)) }, close)
            DownloadMenuItem({ onAction(HomeAction.Download(track)) }, close)
        },
    )
}

private fun carouselItems(items: HomeItems): List<MqCarouselItem> =
    when (items) {
        is HomeItems.Albums -> {
            items.albums.map {
                MqCarouselItem(
                    it.id,
                    it.artwork,
                    it.title,
                    it.albumArtists.joinToString { a ->
                        a.name
                    },
                    it.artwork?.blurHash,
                )
            }
        }

        is HomeItems.Artists -> {
            items.artists.map { MqCarouselItem(it.id, it.artwork, it.name, null, it.artwork?.blurHash) }
        }

        is HomeItems.Playlists -> {
            items.playlists.map { MqCarouselItem(it.id, it.artwork, it.name, null, it.artwork?.blurHash) }
        }

        is HomeItems.Tracks, is HomeItems.Shortcuts -> {
            emptyList()
        }
    }

private fun open(
    id: MediaId,
    items: HomeItems,
    navigation: HomeNavigation,
) = when (items) {
    is HomeItems.Albums -> navigation.onOpenAlbum(id)
    is HomeItems.Artists -> navigation.onOpenArtist(id)
    is HomeItems.Playlists -> navigation.onOpenPlaylist(id)
    is HomeItems.Tracks, is HomeItems.Shortcuts -> Unit
}

/**
 * For each row, the albums whose cover flies into the album page: an album can be in several rows, and only its first
 * card may carry the shared cover (two visible covers with one key break the transition).
 */
internal fun flyingCovers(rows: List<List<Album>>): List<Set<MediaId>> {
    val seen = mutableSetOf<MediaId>()
    return rows.map { row -> row.filter { seen.add(it.id) }.mapTo(HashSet()) { it.id } }
}
