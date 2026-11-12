package io.github.kentprodan.mediatheq.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.YearAlbums
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqLoadingIndicator
import io.github.kentprodan.mediatheq.core.designsystem.component.bottomBarsPadding
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.navigation.HomeSectionListKey
import io.github.kentprodan.mediatheq.core.navigation.RecordLabelKey
import io.github.kentprodan.mediatheq.core.navigation.YearKey
import io.github.kentprodan.mediatheq.core.ui.AlbumCard
import io.github.kentprodan.mediatheq.core.ui.ArtistRow
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.FavoriteMenuItem
import io.github.kentprodan.mediatheq.core.ui.GenreCard
import io.github.kentprodan.mediatheq.core.ui.GenreRow
import io.github.kentprodan.mediatheq.core.ui.NothingDownloaded
import io.github.kentprodan.mediatheq.core.ui.PlaylistRow
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.RecordLabelRow
import io.github.kentprodan.mediatheq.core.ui.SelectionToolbar
import io.github.kentprodan.mediatheq.core.ui.SelectionToolbarSpace
import io.github.kentprodan.mediatheq.core.ui.TrackRow
import io.github.kentprodan.mediatheq.core.ui.trackSwipes
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.withIndex

@Composable
internal fun LibraryRoute(
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
    onNewPlaylist: () -> Unit,
    onOpenDownloads: () -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
    filter: LibraryFilter,
    /** Back from a list opened inside a tab (from the hub); `null` for a list that is a navigation entry. */
    onBack: (() -> Unit)? = null,
    onOpenYear: (YearKey) -> Unit = {},
    onOpenGenre: (MediaId) -> Unit = {},
    onOpenLabel: (RecordLabelKey) -> Unit = {},
    /** A year's page (#129): the albums of that year. */
    yearPage: YearKey? = null,
    /** A record label's page (#131): the albums of that label. */
    labelPage: RecordLabelKey? = null,
    /** A Home section's full list (#183). */
    sectionPage: HomeSectionListKey? = null,
    viewModel: LibraryViewModel =
        hiltViewModel<LibraryViewModel, LibraryViewModel.Factory>(
            key =
                yearPage?.let { "year-${it.year}" } ?: labelPage?.let { "label-${it.id}" }
                    ?: sectionPage?.let { "section-${it.section.name}" } ?: filter.name,
            creationCallback = { it.create(filter, yearPage, labelPage, sectionPage) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        state,
        viewModel::onAction,
        onOpenAlbum,
        onOpenArtist,
        onOpenPlaylist,
        onNewPlaylist = onNewPlaylist,
        onOpenDownloads = onOpenDownloads,
        onAddToPlaylist = onAddToPlaylist,
        onBack = onBack,
        onOpenYear = onOpenYear,
        onOpenGenre = onOpenGenre,
        onOpenLabel = onOpenLabel,
    )
}

/** Stateless Library list: the list header with sort and view menus, then a pull-to-refresh list or grid. */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
    modifier: Modifier = Modifier,
    onNewPlaylist: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onAddToPlaylist: (List<MediaId>) -> Unit = {},
    /** Back from a list that is not a navigation tab; `null` hides the button. */
    onBack: (() -> Unit)? = null,
    onOpenYear: (YearKey) -> Unit = {},
    onOpenGenre: (MediaId) -> Unit = {},
    onOpenLabel: (RecordLabelKey) -> Unit = {},
) {
    // Back ends selecting first, then closes search in list.
    BackHandler(enabled = state.selection.isActive) { onAction(LibraryAction.ClearSelection) }
    BackHandler(enabled = state.listFilter.search != null && !state.selection.isActive) {
        onAction(LibraryAction.Search(null))
    }
    LaunchedEffect(state.addToPlaylist) {
        state.addToPlaylist?.let {
            onAddToPlaylist(it)
            onAction(LibraryAction.AddToPlaylistShown)
        }
    }
    val swipe =
        trackSwipes(
            onAddToPlaylist,
            onRate = { track, stars -> onAction(LibraryAction.RateTrack(track, stars)) },
            onCommand = { track, right -> onAction(LibraryAction.SwipeTrack(track, right)) },
        )
    // One scroll state per kind of list, kept above the content.
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
        topBar = {
            LibraryHeader(
                state,
                onAction,
                onNewPlaylist,
                onBack,
                onSelect = { onAction(LibraryAction.SelectAll) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The app shell already pads for the navigation bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding)) {
            state.listFilter.search?.let { text -> SearchInList(text, onAction) }
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
                    onOpenDownloads = onOpenDownloads,
                    onSwipe = swipe,
                    onOpenYear = onOpenYear,
                    onOpenGenre = onOpenGenre,
                    onOpenLabel = onOpenLabel,
                )
                if (state.selection.isActive) {
                    SelectionToolbar(
                        state.selection.size,
                        onCommand = { onAction(LibraryAction.OnSelection(it)) },
                        onSelectAll = { onAction(LibraryAction.SelectAll) },
                        onClear = { onAction(LibraryAction.ClearSelection) },
                        modifier = Modifier.align(Alignment.BottomCenter).bottomBarsPadding(),
                    )
                }
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
    onOpenDownloads: () -> Unit,
    onSwipe: (Track, Boolean) -> Unit,
    onOpenYear: (YearKey) -> Unit,
    onOpenGenre: (MediaId) -> Unit,
    onOpenLabel: (RecordLabelKey) -> Unit,
) {
    val selection = state.selection
    // While selecting, the last items can scroll up from under the floating toolbar.
    val bottom = if (selection.isActive) SelectionToolbarSpace else MediatheqSpacing.l
    val listPadding = PaddingValues(bottom = bottom).plusBottomBars()
    val haptics = LocalHapticFeedback.current
    val longPress = { id: MediaId ->
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onAction(LibraryAction.ToggleSelection(id))
        }
    }

    /** A tap toggles while selecting, else [open]s. */
    fun tap(
        id: MediaId,
        open: () -> Unit,
    ): () -> Unit = { if (selection.isActive) onAction(LibraryAction.ToggleSelection(id)) else open() }

    fun selected(id: MediaId): Boolean? = if (selection.isActive) id in selection else null
    when (val content = state.content) {
        LibraryContent.Loading -> {
            MqLoadingIndicator(Modifier.fillMaxSize())
        }

        LibraryContent.NothingDownloaded -> {
            ScrollableEmptyState { NothingDownloaded(onOpenDownloads) }
        }

        LibraryContent.NoMatches -> {
            ScrollableEmptyState {
                MqEmptyState(
                    MqIcons.FilterList,
                    stringResource(R.string.library_no_matches_title),
                    body = stringResource(R.string.library_no_matches_body),
                )
            }
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
                contentPadding =
                    PaddingValues(
                        start = MediatheqSpacing.l,
                        top = MediatheqSpacing.l,
                        end = MediatheqSpacing.l,
                        bottom = bottom,
                    ).plusBottomBars(),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Keys must be saveable in a Bundle (scroll restoration), so the id goes in as text.
                items(content.albums, key = { it.id.toString() }) { album ->
                    AlbumCard(
                        album,
                        onClick = tap(album.id) { onOpenAlbum(album.id) },
                        onLongClick = longPress(album.id),
                        selected = selected(album.id),
                    )
                }
            }
        }

        is LibraryContent.Artists -> {
            LazyColumn(Modifier.fillMaxSize(), states.artists, contentPadding = listPadding) {
                items(content.artists, key = { it.id.toString() }) { artist ->
                    ArtistRow(
                        artist,
                        onClick = tap(artist.id) { onOpenArtist(artist.id) },
                        onLongClick = longPress(artist.id),
                        selected = selected(artist.id),
                    )
                }
            }
        }

        is LibraryContent.Playlists -> {
            LazyColumn(Modifier.fillMaxSize(), states.playlists, contentPadding = listPadding) {
                items(content.playlists, key = { it.id.toString() }) { playlist ->
                    PlaylistRow(
                        playlist,
                        onClick = tap(playlist.id) { onOpenPlaylist(playlist.id) },
                        onLongClick = longPress(playlist.id),
                        selected = selected(playlist.id),
                    )
                }
            }
        }

        is LibraryContent.Songs -> {
            LazyColumn(Modifier.fillMaxSize(), states.songs, contentPadding = listPadding) {
                items(content.tracks, key = { it.id.toString() }) { track ->
                    TrackRow(
                        track,
                        onClick =
                            tap(track.id) {
                                onAction(LibraryAction.PlayTrack(track))
                            },
                        showCover = true,
                        onLongClick = longPress(track.id),
                        selected = selected(track.id),
                        menuItems = { close ->
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
                        },
                        onSwipe = { onSwipe(track, it) },
                    )
                }
            }
        }

        is LibraryContent.Years -> {
            YearsList(content.years, listPadding, onOpenYear)
        }

        is LibraryContent.Genres -> {
            if (state.genresGrid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.albumColumns),
                    contentPadding =
                        PaddingValues(
                            start = MediatheqSpacing.l,
                            top = MediatheqSpacing.l,
                            end = MediatheqSpacing.l,
                            bottom = bottom,
                        ).plusBottomBars(),
                    horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
                    verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(content.genres, key = { it.id.toString() }) { genre ->
                        GenreCard(genre, onClick = { onOpenGenre(genre.id) })
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                    items(content.genres, key = { it.id.toString() }) { genre ->
                        GenreRow(genre, onClick = { onOpenGenre(genre.id) })
                    }
                }
            }
        }

        is LibraryContent.RecordLabels -> {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                items(content.labels, key = { it.id.toString() }) { label ->
                    RecordLabelRow(label, onClick = { onOpenLabel(RecordLabelKey(label.id, label.name)) })
                }
            }
        }
    }
}

/** The Years list (#129): each year with its album count; a tap opens its albums. */
@Composable
private fun YearsList(
    years: List<YearAlbums>,
    contentPadding: PaddingValues,
    onOpenYear: (YearKey) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        // Unknown year (null) gets its own text key: keys must be saveable.
        items(years, key = { it.year?.toString() ?: "unknown" }) { year ->
            ListItem(
                headlineContent = {
                    Text(year.year?.toString() ?: stringResource(R.string.library_unknown_year))
                },
                supportingContent = {
                    Text(pluralStringResource(R.plurals.library_year_albums, year.albums.size, year.albums.size))
                },
                leadingContent = { Icon(MqIcons.CalendarMonth, contentDescription = null) },
                modifier = Modifier.clickable { onOpenYear(YearKey(year.year)) },
            )
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

/** Search in list (#123): narrows the shown items by name as you type; the X closes it. */
@Composable
private fun SearchInList(
    text: String,
    onAction: (LibraryAction) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // The field owns its text and cursor; the list's state answers a frame late and would move the cursor (#186).
    // The ViewModel never pushes text back, only closes the search.
    val field = rememberTextFieldState(text)
    val applied by rememberUpdatedState(text)
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }
            .withIndex()
            .filter { (index, text) -> index > 0 || text != applied }
            .collect { onAction(LibraryAction.Search(it.value)) }
    }
    OutlinedTextField(
        field,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MediatheqSpacing.l)
                .padding(bottom = MediatheqSpacing.s)
                .focusRequester(focus),
        placeholder = { Text(stringResource(R.string.library_search_in_list)) },
        leadingIcon = { Icon(MqIcons.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { onAction(LibraryAction.Search(null)) }) {
                Icon(MqIcons.Close, contentDescription = stringResource(R.string.library_close_search))
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}
