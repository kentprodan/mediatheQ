package io.github.kentprodan.mediatheq.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.FilterControlStyle
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFilterBar
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFilterBarStyle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqLoadingIndicator
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.AlbumCard
import io.github.kentprodan.mediatheq.core.ui.ArtistRow
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.FavoriteMenuItem
import io.github.kentprodan.mediatheq.core.ui.PlaylistRow
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.TrackRow

@Composable
internal fun LibraryRoute(
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
    onNewPlaylist: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(state, viewModel::onAction, onOpenAlbum, onOpenArtist, onOpenPlaylist, onNewPlaylist = onNewPlaylist)
}

/** Stateless Library tab: top bar with sort and view menus, filter bar, pull-to-refresh list or grid. */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
    modifier: Modifier = Modifier,
    onNewPlaylist: () -> Unit = {},
) {
    // One scroll state per tab, kept above the content so switching tabs and back keeps each position.
    val albumsState = rememberLazyGridState()
    val artistsState = rememberLazyListState()
    val playlistsState = rememberLazyListState()
    val songsState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshErrorText = stringResource(R.string.library_refresh_failed)
    LaunchedEffect(state.refreshError) {
        if (state.refreshError != null) {
            snackbarHostState.showSnackbar(refreshErrorText)
            onAction(LibraryAction.ErrorShown)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { LibraryTopBar(state, onAction, onNewPlaylist) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The app shell already pads for the navigation bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding)) {
            MqFilterBar(
                labels = LibraryFilter.entries.map { stringResource(it.label) },
                selectedIndex = state.filter.ordinal,
                onSelect = { onAction(LibraryAction.SelectFilter(LibraryFilter.entries[it])) },
                style =
                    when (state.filterStyle) {
                        FilterControlStyle.Chips -> MqFilterBarStyle.Chips
                        FilterControlStyle.ConnectedButtons -> MqFilterBarStyle.ConnectedButtons
                    },
                modifier = Modifier.padding(bottom = MediatheqSpacing.s),
            )
            PullToRefreshBox(
                // The full-screen loading indicator already shows progress; no second spinner on top.
                isRefreshing = state.refreshing && state.content !is LibraryContent.Loading,
                onRefresh = { onAction(LibraryAction.Refresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                LibraryContentView(
                    state,
                    onAction = onAction,
                    onRetry = { onAction(LibraryAction.Refresh) },
                    states = ScrollStates(albumsState, artistsState, playlistsState, songsState),
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                    onOpenPlaylist = onOpenPlaylist,
                )
            }
        }
    }
}

private class ScrollStates(
    val albums: LazyGridState,
    val artists: LazyListState,
    val playlists: LazyListState,
    val songs: LazyListState,
)

@Composable
private fun LibraryContentView(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onRetry: () -> Unit,
    states: ScrollStates,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
) {
    val listPadding = PaddingValues(bottom = MediatheqSpacing.l)
    when (val content = state.content) {
        LibraryContent.Loading -> {
            MqLoadingIndicator(Modifier.fillMaxSize())
        }

        LibraryContent.Empty -> {
            ScrollableEmptyState {
                MqEmptyState(
                    MqIcons.MusicNote,
                    stringResource(R.string.library_empty_title),
                    body = stringResource(R.string.library_empty_body),
                    actionLabel = stringResource(R.string.library_sync_now),
                    onAction = onRetry,
                )
            }
        }

        LibraryContent.Offline -> {
            ScrollableEmptyState {
                MqEmptyState(
                    MqIcons.MusicNote,
                    stringResource(R.string.library_offline_title),
                    body = stringResource(R.string.library_offline_body),
                    actionLabel = stringResource(R.string.library_retry),
                    onAction = onRetry,
                )
            }
        }

        is LibraryContent.Error -> {
            ScrollableEmptyState {
                MqEmptyState(
                    MqIcons.MusicNote,
                    stringResource(R.string.library_error_title),
                    body = stringResource(R.string.library_error_body),
                    actionLabel = stringResource(R.string.library_retry),
                    onAction = onRetry,
                )
            }
        }

        is LibraryContent.Albums -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(state.albumColumns),
                state = states.albums,
                contentPadding = PaddingValues(MediatheqSpacing.l),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Keys must be saveable in a Bundle (scroll restoration), so the id goes in as text.
                items(content.albums, key = { it.id.toString() }) { album ->
                    AlbumCard(album, onClick = { onOpenAlbum(album.id) })
                }
            }
        }

        is LibraryContent.Artists -> {
            LazyColumn(Modifier.fillMaxSize(), states.artists, contentPadding = listPadding) {
                items(content.artists, key = { it.id.toString() }) { artist ->
                    ArtistRow(artist, onClick = { onOpenArtist(artist.id) })
                }
            }
        }

        is LibraryContent.Playlists -> {
            LazyColumn(Modifier.fillMaxSize(), states.playlists, contentPadding = listPadding) {
                items(content.playlists, key = { it.id.toString() }) { playlist ->
                    PlaylistRow(playlist, onClick = { onOpenPlaylist(playlist.id) })
                }
            }
        }

        is LibraryContent.Songs -> {
            LazyColumn(Modifier.fillMaxSize(), states.songs, contentPadding = listPadding) {
                items(content.tracks, key = { it.id.toString() }) { track ->
                    // shortcut: tapping a song does nothing yet; playing from the Songs tab needs its own queue rule.
                    TrackRow(track, onClick = {}, showCover = true, menuItems = { close ->
                        QueueMenuItems(
                            onPlayNext = { onAction(LibraryAction.PlayNext(track)) },
                            onAddToQueue = { onAction(LibraryAction.AddToQueue(track)) },
                            close = close,
                        )
                        FavoriteMenuItem(
                            track.userData.isFavorite,
                            { onAction(LibraryAction.SetFavorite(track, it)) },
                            close,
                        )
                        DownloadMenuItem({ onAction(LibraryAction.Download(track)) }, close)
                    })
                }
            }
        }
    }
}

/** Empty states must scroll so pull-to-refresh still works on them; the full height keeps them centered. */
@Composable
private fun ScrollableEmptyState(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.verticalScroll(rememberScrollState()).height(maxHeight)) { content() }
    }
}

@Composable
private fun LibraryTopBar(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    var sortMenu by rememberSaveable { mutableStateOf(false) }
    var viewMenu by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.library_title)) },
        actions = {
            if (state.filter == LibraryFilter.Playlists) {
                IconButton(onClick = onNewPlaylist) {
                    Icon(MqIcons.Add, contentDescription = stringResource(R.string.library_new_playlist))
                }
            }
            if (state.filter.sorts.size > 1) {
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(MqIcons.Sort, contentDescription = stringResource(R.string.library_sort))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        state.filter.sorts.forEach { sort ->
                            CheckedMenuItem(stringResource(sort.label), checked = sort == state.sort) {
                                onAction(LibraryAction.SelectSort(sort))
                                sortMenu = false
                            }
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { viewMenu = true }) {
                    Icon(MqIcons.GridView, contentDescription = stringResource(R.string.library_view_options))
                }
                DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                    if (state.filter == LibraryFilter.Albums) {
                        LibraryPreferences.ALBUM_COLUMNS.forEach { columns ->
                            CheckedMenuItem(
                                stringResource(R.string.library_columns, columns),
                                checked = columns == state.albumColumns,
                            ) {
                                onAction(LibraryAction.SetAlbumColumns(columns))
                                viewMenu = false
                            }
                        }
                        HorizontalDivider()
                    }
                    FilterControlStyle.entries.forEach { style ->
                        CheckedMenuItem(stringResource(style.label), checked = style == state.filterStyle) {
                            onAction(LibraryAction.SetFilterStyle(style))
                            viewMenu = false
                        }
                    }
                }
            }
        },
    )
}

/** A menu choice with a check mark on the selected one (selection is not shown by color alone). */
@Composable
private fun CheckedMenuItem(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = { if (checked) Icon(MqIcons.Check, contentDescription = null) else Box(Modifier) },
        // TalkBack announces which option is active.
        modifier =
            Modifier.semantics {
                selected = checked
                role = Role.RadioButton
            },
    )
}

private val LibraryFilter.label: Int
    get() =
        when (this) {
            LibraryFilter.Albums -> R.string.library_filter_albums
            LibraryFilter.Artists -> R.string.library_filter_artists
            LibraryFilter.Playlists -> R.string.library_filter_playlists
            LibraryFilter.Songs -> R.string.library_filter_songs
        }

private val LibrarySort.label: Int
    get() =
        when (this) {
            LibrarySort.Name -> R.string.library_sort_name
            LibrarySort.RecentlyAdded -> R.string.library_sort_recently_added
            LibrarySort.Year -> R.string.library_sort_year
            LibrarySort.Artist -> R.string.library_sort_artist
            LibrarySort.Album -> R.string.library_sort_album
        }

private val FilterControlStyle.label: Int
    get() =
        when (this) {
            FilterControlStyle.Chips -> R.string.library_style_chips
            FilterControlStyle.ConnectedButtons -> R.string.library_style_buttons
        }
