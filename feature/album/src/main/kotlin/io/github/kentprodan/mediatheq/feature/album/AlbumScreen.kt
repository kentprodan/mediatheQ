package io.github.kentprodan.mediatheq.feature.album

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.AlbumHeaderBackground
import io.github.kentprodan.mediatheq.core.data.AlbumSection
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.CoverColorCache
import io.github.kentprodan.mediatheq.core.designsystem.theme.LocalMqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.rememberCoverColors
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.ui.AddToPlaylistMenuItem
import io.github.kentprodan.mediatheq.core.ui.DiscHeader
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.FavoriteMenuItem
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.SelectableList
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
import io.github.kentprodan.mediatheq.core.ui.TrackRow
import io.github.kentprodan.mediatheq.core.ui.rememberSelectLongPress
import io.github.kentprodan.mediatheq.core.ui.stateOf
import io.github.kentprodan.mediatheq.core.ui.trackSwipes

@Composable
internal fun AlbumRoute(
    albumId: MediaId,
    onBack: () -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    viewModel: AlbumViewModel =
        hiltViewModel<AlbumViewModel, AlbumViewModel.Factory>(key = albumId.toString(), creationCallback = {
            it.create(albumId)
        }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlbumScreen(
        albumId,
        state,
        viewModel::onAction,
        onBack,
        onAddToPlaylist = onAddToPlaylist,
        onOpenArtist = onOpenArtist,
        onOpenAlbum = onOpenAlbum,
    )
}

/**
 * Stateless album page (#132): a collapsing top bar over the header (background, title, facts, buttons), then the
 * sections in the chosen order: tracks by disc, more from the artist, about.
 */
@Composable
fun AlbumScreen(
    albumId: MediaId,
    state: AlbumUiState,
    onAction: (AlbumAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (List<MediaId>) -> Unit = {},
    onOpenArtist: (MediaId) -> Unit = {},
    onOpenAlbum: (MediaId) -> Unit = {},
) {
    val longPress = rememberSelectLongPress<MediaId> { onAction(AlbumAction.ToggleSelection(it)) }
    val swipe =
        trackSwipes(
            onAddToPlaylist,
            onRate = { track, stars -> onAction(AlbumAction.RateTrack(track, stars)) },
            onCommand = { track, right -> onAction(AlbumAction.SwipeTrack(track, right)) },
        )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val content = state as? AlbumUiState.Content
    val drawsUnderBar = content != null && content.page.headerBackground != AlbumHeaderBackground.None
    val colorCache = remember { CoverColorCache() }
    val artwork = content?.album?.artwork
    val coverColors = rememberCoverColors(artwork?.let { "${it.itemId}/${it.tag}" }, artwork, colorCache)
    val coverColor =
        coverColors
            ?.scheme(LocalMqTheme.current.isDark, vibrant = false)
            ?.primaryContainer
            ?.takeIf { content?.page?.headerBackground == AlbumHeaderBackground.CoverColor }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                // The header shows the title; the bar names the album only once it has nearly collapsed.
                title = {
                    Text(
                        content?.title?.name.orEmpty(),
                        Modifier.graphicsLayer { alpha = titleAlpha(scrollBehavior.state.collapsedFraction) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.album_back))
                    }
                },
                colors =
                    if (drawsUnderBar) {
                        TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    } else {
                        TopAppBarDefaults.topAppBarColors()
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
                SelectableList(
                    state.selection,
                    onCommand = { command ->
                        if (command == SelectionCommand.AddToPlaylist) {
                            onAddToPlaylist(state.selection.pick(state.tracks) { it.id }.map { it.id })
                            onAction(AlbumAction.ClearSelection)
                        } else {
                            onAction(AlbumAction.OnSelection(command))
                        }
                    },
                    onSelectAll = { onAction(AlbumAction.SelectAll) },
                    onClear = { onAction(AlbumAction.ClearSelection) },
                ) { bottomSpace ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                // A cover image or color draws under the top bar; the header pads itself.
                                top = if (drawsUnderBar) 0.dp else padding.calculateTopPadding(),
                                bottom = bottomSpace,
                            ).plusBottomBars(),
                    ) {
                        item(key = "header") {
                            AlbumHeader(
                                albumId,
                                state,
                                topPadding = if (drawsUnderBar) padding.calculateTopPadding() else 0.dp,
                                coverColor = coverColor,
                                onAction = onAction,
                                onOpenArtist = onOpenArtist,
                                onAddToPlaylist = onAddToPlaylist,
                            )
                        }
                        state.sections.forEach { section ->
                            when (section) {
                                AlbumSection.Tracks -> {
                                    tracks(state, onAction, longPress, swipe, onAddToPlaylist)
                                }

                                AlbumSection.MoreFromArtist -> {
                                    item(key = "more") {
                                        MoreFromArtist(
                                            state.album.albumArtists
                                                .first()
                                                .name,
                                            state.moreAlbums,
                                            onOpenAlbum,
                                        )
                                    }
                                }

                                AlbumSection.About -> {
                                    item(key = "about") { AboutSection(state) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.tracks(
    state: AlbumUiState.Content,
    onAction: (AlbumAction) -> Unit,
    longPress: (MediaId) -> () -> Unit,
    swipe: (Track, Boolean) -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
) {
    state.discs.forEach { disc ->
        if (state.showDiscHeaders) item(key = "disc-${disc.number}") { DiscHeader(disc.number) }
        items(disc.tracks, key = { it.id.toString() }) { track ->
            TrackRow(
                track,
                onClick = {
                    if (state.selection.isActive) {
                        onAction(AlbumAction.ToggleSelection(track.id))
                    } else {
                        onAction(AlbumAction.PlayTrack(track.id))
                    }
                },
                onLongClick = longPress(track.id),
                selected = state.selection.stateOf(track.id),
                onSwipe = { swipe(track, it) },
                isCurrent = track.id == state.currentTrackId,
                showTrackNumber = state.page.showTrackNumbers,
                showCover = state.page.showTrackThumbnails,
                multilineTitle = state.page.multilineTrackTitles,
                // The artists show only when asked for or when they are not the album's (#132).
                showArtists = state.showsArtists(track),
                menuItems = { close ->
                    QueueMenuItems(
                        onPlayNext = { onAction(AlbumAction.PlayNext(track.id)) },
                        onAddToQueue = { onAction(AlbumAction.AddToQueue(track.id)) },
                        close = close,
                    )
                    AddToPlaylistMenuItem({ onAddToPlaylist(listOf(track.id)) }, close)
                    FavoriteMenuItem(
                        track.userData.isFavorite,
                        { onAction(AlbumAction.SetFavorite(track.id, it)) },
                        close,
                    )
                    DownloadMenuItem({ onAction(AlbumAction.Download(track.id)) }, close)
                },
            )
        }
    }
}

/** Hidden while the bar is open, fading in over the last part of the collapse. */
private fun titleAlpha(collapsedFraction: Float): Float =
    ((collapsedFraction - TITLE_FADE_START) / (1f - TITLE_FADE_START)).coerceIn(0f, 1f)

private const val TITLE_FADE_START = 0.7f
