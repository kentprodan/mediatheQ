package io.github.kentprodan.mediatheq.feature.tv.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvListItem
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMessage
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.navigation.YearKey
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvAlbumCard
import kotlinx.coroutines.launch

/** Where a card leads. */
data class TvLibraryNavigation(
    val onOpenAlbum: (MediaId) -> Unit = {},
    val onOpenArtist: (MediaId) -> Unit = {},
    val onOpenPlaylist: (MediaId) -> Unit = {},
    val onOpenGenre: (MediaId) -> Unit = {},
)

/** The tabs of the TV Library, in order. */
enum class TvLibraryTab(
    val filter: LibraryFilter,
    @param:StringRes val label: Int,
) {
    Albums(LibraryFilter.Albums, R.string.tv_library_albums),
    Artists(LibraryFilter.Artists, R.string.tv_library_artists),
    Playlists(LibraryFilter.Playlists, R.string.tv_library_playlists),
    Genres(LibraryFilter.Genres, R.string.tv_library_genres),
}

/**
 * The Library on TV (#237): tabs on top (focusing a tab shows it, as TV tabs do), each tab its own list whose scroll
 * position and focused card are kept while another tab shows.
 */
@Composable
fun TvLibraryRoute(navigation: TvLibraryNavigation) =
    TvLibraryTabsScreen { tab, cameBack ->
        val viewModel =
            hiltViewModel<TvLibraryViewModel, TvLibraryViewModel.Factory>(key = tab.name) {
                it.create(tab.filter, null, null)
            }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        TvLibraryList(state, viewModel::onAction, navigation, restoreFocus = cameBack)
    }

/** The tabs and the shown tab's [list]; `cameBack` says the screen just opened (back from a detail page). */
@Composable
fun TvLibraryTabsScreen(list: @Composable (tab: TvLibraryTab, cameBack: Boolean) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(TvLibraryTab.Albums) }
    // Coming back from a detail page the tab's last card takes focus; switching tabs leaves focus on the tabs.
    var cameBack by remember { mutableStateOf(true) }
    val tabs = rememberSaveableStateHolder()
    val selectedTab = remember { FocusRequester() }
    var inList by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TvLibraryTabs(tab, selectedTab) {
            tab = it
            cameBack = false
        }
        Box(Modifier.onFocusChanged { inList = it.hasFocus }.focusGroup()) {
            tabs.SaveableStateProvider(tab.name) { list(tab, cameBack) }
        }
    }
    // Deep in a list Up only scrolls: Back goes up to the tabs first, the next Back on to the shell (#348).
    BackHandler(enabled = inList) { runCatching { selectedTab.requestFocus() } }
}

@Composable
fun TvLibraryTabs(
    selected: TvLibraryTab,
    selectedFocus: FocusRequester,
    onSelect: (TvLibraryTab) -> Unit,
) {
    TabRow(selected.ordinal, Modifier.padding(start = EDGE, top = 24.dp)) {
        TvLibraryTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onFocus = { if (tab != selected) onSelect(tab) },
                modifier = if (tab == selected) Modifier.focusRequester(selectedFocus) else Modifier,
                // The selected tab's pill is dark while the row has no focus: light text on it.
                colors =
                    TabDefaults.pillIndicatorTabColors(
                        selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ) {
                Text(
                    stringResource(tab.label),
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** A genre's or a year's albums (#237): the album grid under the genre's name or the year. */
@Composable
fun TvLibraryPageRoute(
    navigation: TvLibraryNavigation,
    genre: MediaId? = null,
    year: YearKey? = null,
) {
    val viewModel =
        hiltViewModel<TvLibraryViewModel, TvLibraryViewModel.Factory>(key = "$genre/$year") {
            it.create(LibraryFilter.Albums, genre, year)
        }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val genreName by viewModel.genreName.collectAsStateWithLifecycle()
    val title =
        when {
            genre != null -> genreName.orEmpty()
            year?.year != null -> year.year.toString()
            else -> stringResource(R.string.tv_library_unknown_year)
        }
    TvLibraryList(state, viewModel::onAction, navigation, restoreFocus = true, title = title, focusFirst = true)
}

/**
 * One list: its sort button (and [title] on a page), then its cards in a 6-column grid with the A-Z strip at the side
 * when sorted by name, or the loading, empty or error state.
 */
@Composable
fun TvLibraryList(
    state: TvLibraryUiState,
    onAction: (TvLibraryAction) -> Unit,
    navigation: TvLibraryNavigation,
    modifier: Modifier = Modifier,
    restoreFocus: Boolean = false,
    title: String? = null,
    /** A page opened from a card: its first card takes focus (a tab leaves it on the tabs or the drawer). */
    focusFirst: Boolean = false,
) {
    var sorting by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = EDGE, end = EDGE, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            title?.let {
                Text(it, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.weight(1f))
            if (state.filter.sorts.size > 1) {
                MqTvButton(
                    stringResource(R.string.tv_library_sort, stringResource(state.sort.label)),
                    { sorting = true },
                )
            }
        }
        when (val content = state.content) {
            TvLibraryContent.Loading -> {
                MqTvMessage(
                    stringResource(R.string.tv_library_loading_title),
                    stringResource(R.string.tv_library_loading_body),
                )
            }

            TvLibraryContent.Empty -> {
                MqTvMessage(
                    stringResource(R.string.tv_library_empty_title),
                    stringResource(R.string.tv_library_empty_body),
                    action = stringResource(R.string.tv_library_sync_now),
                    onAction = { onAction(TvLibraryAction.Sync) },
                    // Not while the user walks along the tabs.
                    focusAction = restoreFocus,
                )
            }

            is TvLibraryContent.Error -> {
                MqTvMessage(
                    stringResource(R.string.tv_library_error_title),
                    stringResource(R.string.tv_library_error_body),
                    action = stringResource(R.string.tv_library_sync_now),
                    onAction = { onAction(TvLibraryAction.Sync) },
                    // Not while the user walks along the tabs.
                    focusAction = restoreFocus,
                )
            }

            is TvLibraryContent.Items -> {
                Grid(content, navigation, restoreFocus, focusFirst, state.sort)
            }
        }
    }
    if (sorting) {
        SortMenu(
            state,
            onDismiss = { sorting = false },
            onReverse = { onAction(TvLibraryAction.ReverseSort) },
        ) { onAction(TvLibraryAction.SetSort(it)) }
    }
}

@Composable
private fun Grid(
    content: TvLibraryContent.Items,
    navigation: TvLibraryNavigation,
    restoreFocus: Boolean,
    focusFirst: Boolean,
    sort: LibrarySort,
) {
    val focus = rememberFocusMemory()
    val grid = rememberLazyGridState()
    // A new sort starts at the top; the cards keep their keys, so the grid would stay on the old first card.
    var shownSort by rememberSaveable { mutableStateOf(sort) }
    LaunchedEffect(sort) {
        if (sort != shownSort) grid.scrollToItem(0)
        shownSort = sort
    }
    val scope = rememberCoroutineScope()
    val first =
        content.items
            .first()
            .id
            .toString()
    Row(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Fixed(COLUMNS),
            // Down from the tabs or Left from the strip: the card focused last, else the first. A request for one card
            // (the strip's jump) is left alone.
            Modifier
                .weight(1f)
                .focusProperties {
                    onEnter =
                        { if (requestedFocusDirection != FocusDirection.Enter) focus.restore(first) }
                }.focusGroup(),
            state = grid,
            contentPadding = PaddingValues(start = EDGE, end = 16.dp, top = 24.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(content.items, key = {
                it.id.toString()
            }) { item -> Card(item, navigation, Modifier.focusMemory(focus, item.id.toString())) }
        }
        if (content.letters.size > 1) {
            Strip(content.letters) { letter ->
                scope.launch {
                    grid.scrollToItem(letter.index)
                    withFrameNanos {}
                    focus.focus(content.items[letter.index].id.toString())
                }
            }
        }
    }
    if (restoreFocus) {
        LaunchedEffect(Unit) {
            // The grid composes its cards when laid out, after this effect starts.
            withFrameNanos {}
            focus.restore(first.takeIf { focusFirst })
        }
    }
}

@Composable
private fun Card(
    item: TvLibraryItem,
    navigation: TvLibraryNavigation,
    modifier: Modifier,
) {
    val subtitle =
        item.albumCount?.let { pluralStringResource(R.plurals.tv_library_albums_count, it, it) } ?: item.subtitle
    MqTvAlbumCard(
        item.title,
        item.artwork,
        onClick = {
            when (item.kind) {
                TvLibraryItem.Kind.Album -> navigation.onOpenAlbum(item.id)
                TvLibraryItem.Kind.Artist -> navigation.onOpenArtist(item.id)
                TvLibraryItem.Kind.Playlist -> navigation.onOpenPlaylist(item.id)
                TvLibraryItem.Kind.Genre -> navigation.onOpenGenre(item.id)
            }
        },
        modifier.fillMaxWidth(),
        subtitle = subtitle,
        placeholder = if (item.kind == TvLibraryItem.Kind.Artist) MqTvIcons.Person else MqTvIcons.MusicNote,
    )
}

/** The A-Z strip: one round key per letter; selecting one jumps to the letter's first card and focuses it. */
@Composable
private fun Strip(
    letters: List<Letter>,
    onJump: (Letter) -> Unit,
) {
    LazyColumn(
        // Back to the strip: the letter used last.
        Modifier.width(56.dp).fillMaxHeight().focusRestorer(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(letters, key = { it.letter }) { letter ->
            Surface(
                onClick = { onJump(letter) },
                modifier = Modifier.size(32.dp),
                shape = ClickableSurfaceDefaults.shape(CircleShape),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(letter.letter.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    state: TvLibraryUiState,
    onDismiss: () -> Unit,
    onReverse: () -> Unit,
    onSort: (LibrarySort) -> Unit,
) {
    val current = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        androidx.tv.material3.Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.width(360.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.tv_library_sort_title),
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                state.filter.sorts.forEach { sort ->
                    MqTvListItem(
                        stringResource(sort.label),
                        {
                            onSort(sort)
                            onDismiss()
                        },
                        if (sort == state.sort) Modifier.focusRequester(current) else Modifier,
                        selected = sort == state.sort,
                    )
                }
                // The direction of the chosen sort (#349), as the phone's Reverse order (#187).
                MqTvListItem(
                    stringResource(R.string.tv_library_sort_reverse),
                    {
                        onReverse()
                        onDismiss()
                    },
                    selected = state.reversed,
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { current.requestFocus() } }
}

@get:StringRes
private val LibrarySort.label: Int
    get() =
        when (this) {
            LibrarySort.Name -> R.string.tv_library_sort_name
            LibrarySort.RecentlyAdded -> R.string.tv_library_sort_recently_added
            LibrarySort.Year -> R.string.tv_library_sort_year
            LibrarySort.Artist -> R.string.tv_library_sort_artist
            LibrarySort.Album -> R.string.tv_library_sort_album
            LibrarySort.AlbumCount -> R.string.tv_library_sort_album_count
            LibrarySort.TrackCount -> R.string.tv_library_sort_track_count
        }

private val EDGE = 48.dp

/** Six covers across at 1080p next to the collapsed drawer (#237). */
private const val COLUMNS = 6
