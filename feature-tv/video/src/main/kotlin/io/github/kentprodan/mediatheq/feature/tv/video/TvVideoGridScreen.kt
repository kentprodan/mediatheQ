package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvListItem
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMessage
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvTabs
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvLetterStrip
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvPosterCard
import kotlinx.coroutines.launch

/** Where the Movies and Series tabs lead (#522). */
data class TvVideoNavigation(
    /** A movie's or a series' page. */
    val onOpen: (MediaId) -> Unit = {},
    val onOpenCollection: (MediaId) -> Unit = {},
    /** A genre's or studio's grid: `studio` is false for a genre. */
    val onOpenGroup: (kind: TvVideoKind, studio: Boolean, name: String) -> Unit = { _, _, _ -> },
)

/** The tabs of Movies and Series (#522, owner decisions): Collections only for movies, Studios called Networks for series. */
enum class TvVideoTab {
    All,
    Collections,
    Genres,
    Favorites,
    Studios,
    ;

    companion object {
        fun of(kind: TvVideoKind): List<TvVideoTab> = entries.filter { kind == TvVideoKind.Movies || it != Collections }
    }
}

@Composable
fun TvMoviesRoute(navigation: TvVideoNavigation) = TvVideoTabsRoute(TvVideoKind.Movies, navigation)

@Composable
fun TvSeriesRoute(navigation: TvVideoNavigation) = TvVideoTabsRoute(TvVideoKind.Series, navigation)

/** Movies or Series as tabs (#522): each tab its own grid, which keeps its scroll, focus, sort and filters. */
@Composable
fun TvVideoTabsRoute(
    kind: TvVideoKind,
    navigation: TvVideoNavigation,
    initial: TvVideoTab = TvVideoTab.All,
    /** Draws a tab; the app's routes by default, plain content in tests and screenshots. */
    tabContent: @Composable (tab: TvVideoTab, cameBack: Boolean) -> Unit = { tab, cameBack ->
        TvVideoTabContent(kind, tab, navigation, cameBack)
    },
) {
    val tabs = TvVideoTab.of(kind)
    MqTvTabs(
        tabs,
        { stringResource(it.label(kind)) },
        { it.name },
        initial = tabs.indexOf(initial),
        content = tabContent,
    )
}

@Composable
private fun TvVideoTabContent(
    kind: TvVideoKind,
    tab: TvVideoTab,
    navigation: TvVideoNavigation,
    cameBack: Boolean,
) = when (tab) {
    TvVideoTab.All -> TvVideosRoute(kind, TvVideoOnly.All, navigation.onOpen, cameBack)
    TvVideoTab.Favorites -> TvVideosRoute(kind, TvVideoOnly.Favorites, navigation.onOpen, cameBack)
    TvVideoTab.Collections -> TvCollectionsRoute(navigation.onOpenCollection, cameBack)
    TvVideoTab.Genres -> TvVideoGroupsRoute(kind, studios = false, navigation, cameBack)
    TvVideoTab.Studios -> TvVideoGroupsRoute(kind, studios = true, navigation, cameBack)
}

@StringRes
private fun TvVideoTab.label(kind: TvVideoKind): Int =
    when (this) {
        TvVideoTab.All -> {
            R.string.tv_video_tab_all
        }

        TvVideoTab.Collections -> {
            R.string.tv_video_collections
        }

        TvVideoTab.Genres -> {
            R.string.tv_video_tab_genres
        }

        TvVideoTab.Favorites -> {
            R.string.tv_video_tab_favorites
        }

        TvVideoTab.Studios -> {
            if (kind ==
                TvVideoKind.Series
            ) {
                R.string.tv_video_tab_networks
            } else {
                R.string.tv_video_tab_studios
            }
        }
    }

/** A kind's grid of [only] its posters: a tab, or a genre's or studio's page (#522). */
@Composable
fun TvVideosRoute(
    kind: TvVideoKind,
    only: TvVideoOnly,
    onOpen: (MediaId) -> Unit,
    restoreFocus: Boolean = true,
) {
    val viewModel =
        hiltViewModel<TvVideosViewModel, TvVideosViewModel.Factory>(key = "$kind/$only") { it.create(kind, only) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvVideoGridScreen(kind, state, viewModel::onAction, onOpen, only = only, restoreFocus = restoreFocus)
}

@Composable
private fun TvCollectionsRoute(
    onOpen: (MediaId) -> Unit,
    restoreFocus: Boolean,
) {
    val viewModel = hiltViewModel<TvCollectionsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvVideoGridScreen(TvVideoKind.Collections, state, viewModel::onAction, onOpen, restoreFocus = restoreFocus)
}

@Composable
private fun TvVideoGroupsRoute(
    kind: TvVideoKind,
    studios: Boolean,
    navigation: TvVideoNavigation,
    restoreFocus: Boolean,
) {
    val viewModel =
        hiltViewModel<TvVideoGroupsViewModel, TvVideoGroupsViewModel.Factory>(key = "$kind/groups/$studios") {
            it.create(kind, studios)
        }
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    TvVideoGroups(kind, groups, restoreFocus) { navigation.onOpenGroup(kind, studios, it) }
}

/** The Genres or Studios / Networks tab (#522): six cards across, each a name, its count and its newest picture. */
@Composable
fun TvVideoGroups(
    kind: TvVideoKind,
    groups: List<TvVideoGroup>?,
    restoreFocus: Boolean,
    onOpen: (String) -> Unit,
) {
    when {
        groups == null -> {
            Unit
        }

        groups.isEmpty() -> {
            MqTvMessage(
                stringResource(R.string.tv_video_no_groups),
                stringResource(R.string.tv_video_no_studios_body),
                focusAction = false,
            )
        }

        else -> {
            val focus = rememberFocusMemory()
            LazyVerticalGrid(
                GridCells.Fixed(COLUMNS),
                Modifier
                    .fillMaxSize()
                    // Down from the tabs: the card focused last, else the first; a request for one card is left alone.
                    .focusProperties {
                        onEnter = {
                            if (requestedFocusDirection != FocusDirection.Enter) focus.restore(groups.first().name)
                        }
                    }.focusGroup(),
                contentPadding = PaddingValues(start = EDGE, end = EDGE, top = 24.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(groups, key = { it.name }, contentType = { "group" }) { group ->
                    MqTvPosterCard(
                        group.name,
                        group.artwork,
                        onClick = { onOpen(group.name) },
                        modifier = Modifier.fillMaxWidth().focusMemory(focus, group.name),
                        subtitle = pluralStringResource(kind.count, group.count, group.count),
                        placeholder = if (kind == TvVideoKind.Series) MqTvIcons.Series else MqTvIcons.Movie,
                    )
                }
            }
            if (restoreFocus) {
                LaunchedEffect(Unit) {
                    withFrameNanos {}
                    focus.restore(null)
                }
            }
        }
    }
}

@Composable
fun TvCollectionRoute(
    id: MediaId,
    onOpen: (MediaId) -> Unit,
) {
    val viewModel =
        hiltViewModel<TvCollectionViewModel, TvCollectionViewModel.Factory>(key = id.toString()) { it.create(id) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvVideoGridScreen(TvVideoKind.Collection, state, viewModel::onAction, onOpen)
}

/**
 * The Movies or Series grid on TV (#426): the title with the count, Sort and Filter, then six posters across with the
 * A-Z strip at the side while sorted by name. Coming back from a page focuses the poster that opened it.
 */
@Composable
fun TvVideoGridScreen(
    kind: TvVideoKind,
    state: TvVideoGridUiState,
    onAction: (TvVideoGridAction) -> Unit,
    onOpen: (MediaId) -> Unit,
    modifier: Modifier = Modifier,
    /** Which posters the grid holds (#522): the Favorites tab has no favorites filter and its own empty message. */
    only: TvVideoOnly = TvVideoOnly.All,
    /** Focus the poster focused last when the grid opens; a tab switch leaves focus on the tabs (#522). */
    restoreFocus: Boolean = true,
) {
    var menu by remember { mutableStateOf<GridMenu?>(null) }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = EDGE, end = EDGE, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                (state as? TvVideoGridUiState.Content)?.title ?: stringResource(kind.title),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            if (state is TvVideoGridUiState.Content) {
                Text(
                    count(kind, state),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                MqTvButton(
                    stringResource(R.string.tv_video_sort, stringResource(state.sort.label)),
                    { menu = GridMenu.Sort },
                )
                if (kind.filters) {
                    MqTvButton(
                        if (state.filter.active == 0) {
                            stringResource(R.string.tv_video_filter)
                        } else {
                            stringResource(R.string.tv_video_filter_active, state.filter.active)
                        },
                        { menu = GridMenu.Filter },
                    )
                }
            }
        }
        when (state) {
            TvVideoGridUiState.Loading -> {
                Unit
            }

            TvVideoGridUiState.Empty if only == TvVideoOnly.Favorites -> {
                MqTvMessage(
                    stringResource(R.string.tv_video_no_favorites),
                    stringResource(R.string.tv_video_no_favorites_body),
                    focusAction = false,
                )
            }

            TvVideoGridUiState.Empty -> {
                MqTvMessage(
                    stringResource(kind.empty),
                    stringResource(R.string.tv_video_empty_body),
                    action = stringResource(R.string.tv_video_sync_now),
                    onAction = { onAction(TvVideoGridAction.Sync) },
                    focusAction = false,
                )
            }

            TvVideoGridUiState.Error -> {
                MqTvMessage(
                    stringResource(R.string.tv_video_error_title),
                    stringResource(R.string.tv_video_error_body),
                    action = stringResource(R.string.tv_video_sync_now),
                    onAction = { onAction(TvVideoGridAction.Sync) },
                    focusAction = false,
                )
            }

            is TvVideoGridUiState.Content -> {
                if (state.posters.isEmpty()) {
                    MqTvMessage(
                        stringResource(R.string.tv_video_nothing_matches),
                        stringResource(R.string.tv_video_nothing_matches_body),
                        action = stringResource(R.string.tv_video_clear_filters),
                        onAction = { onAction(TvVideoGridAction.ClearFilters) },
                    )
                } else {
                    Grid(kind, state, onOpen, restoreFocus)
                }
            }
        }
    }
    val content = state as? TvVideoGridUiState.Content
    when (menu) {
        GridMenu.Sort -> {
            content?.let {
                SortMenu(kind, it.sort, { menu = null }) { s -> onAction(TvVideoGridAction.SetSort(s)) }
            }
        }

        GridMenu.Filter -> {
            content?.let { FilterMenu(it, onAction, favorites = only != TvVideoOnly.Favorites) { menu = null } }
        }

        null -> {
            Unit
        }
    }
}

@Composable
private fun count(
    kind: TvVideoKind,
    state: TvVideoGridUiState.Content,
): String {
    val shown = state.posters.size
    val all =
        pluralStringResource(
            kind.count,
            state.total,
            state.total,
        )
    return if (shown == state.total) all else stringResource(R.string.tv_video_count_filtered, shown, all)
}

@Composable
private fun Grid(
    kind: TvVideoKind,
    state: TvVideoGridUiState.Content,
    onOpen: (MediaId) -> Unit,
    restoreFocus: Boolean,
) {
    val focus = rememberFocusMemory()
    val grid = rememberLazyGridState()
    // A new sort or filter starts at the top; the posters keep their keys, so the grid would stay on the old first one.
    val order = Triple(state.sort, state.filter, state.seed)
    var shownOrder by rememberSaveable { mutableStateOf(order.toString()) }
    LaunchedEffect(order) {
        if (order.toString() != shownOrder) grid.scrollToItem(0)
        shownOrder = order.toString()
    }
    val scope = rememberCoroutineScope()
    val first =
        state.posters
            .first()
            .id
            .toString()
    Row(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Fixed(COLUMNS),
            // Down from the buttons or Left from the strip: the poster focused last, else the first. A request for one
            // poster (the strip's jump) is left alone.
            Modifier
                .weight(1f)
                .focusProperties {
                    onEnter = { if (requestedFocusDirection != FocusDirection.Enter) focus.restore(first) }
                }.focusGroup(),
            state = grid,
            contentPadding = PaddingValues(start = EDGE, end = 16.dp, top = 24.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(state.posters, key = { it.id.toString() }, contentType = { "poster" }) { poster ->
                MqTvPosterCard(
                    poster.title,
                    poster.artwork,
                    onClick = { onOpen(poster.id) },
                    modifier = Modifier.fillMaxWidth().focusMemory(focus, poster.id.toString()),
                    subtitle = poster.year?.toString(),
                    watched = poster.watched,
                    progress = poster.progress,
                    placeholder = if (kind == TvVideoKind.Series) MqTvIcons.Series else MqTvIcons.Movie,
                )
            }
        }
        if (state.letters.size > 1) {
            MqTvLetterStrip(state.letters) { letter ->
                scope.launch {
                    grid.scrollToItem(letter.index)
                    withFrameNanos {}
                    focus.focus(state.posters[letter.index].id.toString())
                }
            }
        }
    }
    if (restoreFocus) {
        LaunchedEffect(Unit) {
            // The grid composes its posters when laid out, after this effect starts.
            withFrameNanos {}
            focus.restore(null)
        }
    }
}

private enum class GridMenu { Sort, Filter }

@Composable
private fun SortMenu(
    kind: TvVideoKind,
    current: TvVideoSort,
    onDismiss: () -> Unit,
    onSort: (TvVideoSort) -> Unit,
) {
    val sorts = kind.sorts
    MenuDialog(stringResource(R.string.tv_video_sort_title), onDismiss) { selected ->
        items(sorts) { sort ->
            MqTvListItem(
                stringResource(sort.label),
                {
                    onSort(sort)
                    onDismiss()
                },
                if (sort == current) Modifier.focusRequester(selected) else Modifier,
                selected = sort == current,
            )
        }
    }
}

/** The filters: watched state, favorites, then a genre or a decade from their own lists, and Clear. */
@Composable
private fun FilterMenu(
    state: TvVideoGridUiState.Content,
    onAction: (TvVideoGridAction) -> Unit,
    favorites: Boolean,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(FilterPage.Main) }
    val filter = state.filter
    when (page) {
        FilterPage.Main -> {
            MenuDialog(stringResource(R.string.tv_video_filter_title), onDismiss) { first ->
                items(TvWatched.entries) { watched ->
                    MqTvListItem(
                        stringResource(watched.label),
                        { onAction(TvVideoGridAction.SetWatched(watched)) },
                        if (watched == TvWatched.All) Modifier.focusRequester(first) else Modifier,
                        selected = filter.watched == watched,
                    )
                }
                if (favorites) {
                    item {
                        MqTvListItem(
                            stringResource(R.string.tv_video_filter_favorites),
                            { onAction(TvVideoGridAction.ToggleFavorites) },
                            selected = filter.favorites,
                        )
                    }
                }
                item {
                    MqTvListItem(
                        stringResource(R.string.tv_video_filter_genre),
                        { page = FilterPage.Genre },
                        supporting = filter.genre ?: stringResource(R.string.tv_video_filter_any),
                        enabled = state.genres.isNotEmpty(),
                    )
                }
                item {
                    MqTvListItem(
                        stringResource(R.string.tv_video_filter_decade),
                        { page = FilterPage.Decade },
                        supporting =
                            filter.decade?.let { stringResource(R.string.tv_video_decade, it) }
                                ?: stringResource(R.string.tv_video_filter_any),
                        enabled = state.decades.isNotEmpty(),
                    )
                }
                if (filter.active > 0) {
                    item {
                        MqTvListItem(
                            stringResource(R.string.tv_video_clear_filters),
                            {
                                onAction(TvVideoGridAction.ClearFilters)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }

        FilterPage.Genre -> {
            ChoiceDialog(
                stringResource(R.string.tv_video_filter_genre),
                state.genres,
                filter.genre,
                label = { it },
                onDismiss = { page = FilterPage.Main },
            ) { onAction(TvVideoGridAction.SetGenre(it)) }
        }

        FilterPage.Decade -> {
            ChoiceDialog(
                stringResource(R.string.tv_video_filter_decade),
                state.decades,
                filter.decade,
                label = { stringResource(R.string.tv_video_decade, it) },
                onDismiss = { page = FilterPage.Main },
            ) { onAction(TvVideoGridAction.SetDecade(it)) }
        }
    }
}

private enum class FilterPage { Main, Genre, Decade }

/** A list of [choices] with Any first; choosing one sets it and goes back to the filters. */
@Composable
private fun <T : Any> ChoiceDialog(
    title: String,
    choices: List<T>,
    current: T?,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onChoose: (T?) -> Unit,
) {
    MenuDialog(title, onDismiss) { selected ->
        item {
            MqTvListItem(
                stringResource(R.string.tv_video_filter_any),
                {
                    onChoose(null)
                    onDismiss()
                },
                if (current == null) Modifier.focusRequester(selected) else Modifier,
                selected = current == null,
            )
        }
        items(choices, key = { it.toString() }) { choice ->
            MqTvListItem(
                label(choice),
                {
                    onChoose(choice)
                    onDismiss()
                },
                if (choice == current) Modifier.focusRequester(selected) else Modifier,
                selected = choice == current,
            )
        }
    }
}

/** A titled list in a dialog; the item given the requester takes focus when it opens. */
@Composable
private fun MenuDialog(
    title: String,
    onDismiss: () -> Unit,
    content: LazyListScope.(FocusRequester) -> Unit,
) {
    val selected = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.width(400.dp).padding(16.dp)) {
                Text(
                    title,
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    content(selected)
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { selected.requestFocus() } }
}

@get:StringRes
private val TvVideoSort.label: Int
    get() =
        when (this) {
            TvVideoSort.Collection -> R.string.tv_video_sort_collection
            TvVideoSort.Name -> R.string.tv_video_sort_name
            TvVideoSort.DateAdded -> R.string.tv_video_sort_date_added
            TvVideoSort.Year -> R.string.tv_video_sort_year
            TvVideoSort.Rating -> R.string.tv_video_sort_rating
            TvVideoSort.Runtime -> R.string.tv_video_sort_runtime
            TvVideoSort.Random -> R.string.tv_video_sort_random
        }

@get:StringRes
private val TvVideoKind.title: Int
    get() =
        when (this) {
            TvVideoKind.Movies -> R.string.tv_video_movies
            TvVideoKind.Series -> R.string.tv_video_series
            TvVideoKind.Collections, TvVideoKind.Collection -> R.string.tv_video_collections
        }

@get:StringRes
private val TvVideoKind.empty: Int
    get() =
        when (this) {
            TvVideoKind.Movies, TvVideoKind.Collection -> R.string.tv_video_no_movies
            TvVideoKind.Series -> R.string.tv_video_no_series
            TvVideoKind.Collections -> R.string.tv_video_no_collections
        }

@get:PluralsRes
private val TvVideoKind.count: Int
    get() =
        when (this) {
            TvVideoKind.Movies, TvVideoKind.Collection -> R.plurals.tv_video_movies_count
            TvVideoKind.Series -> R.plurals.tv_video_series_count
            TvVideoKind.Collections -> R.plurals.tv_video_collections_count
        }

@get:StringRes
private val TvWatched.label: Int
    get() =
        when (this) {
            TvWatched.All -> R.string.tv_video_filter_all
            TvWatched.Unwatched -> R.string.tv_video_filter_unwatched
            TvWatched.Watched -> R.string.tv_video_filter_watched
        }

private val EDGE = 48.dp

/** Six posters across next to the collapsed drawer (owner, #426). */
private const val COLUMNS = 6
