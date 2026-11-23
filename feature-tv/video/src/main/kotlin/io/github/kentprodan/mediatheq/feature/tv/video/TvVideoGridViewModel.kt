package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.tv.Letter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * A TV video grid (#426): every movie or series, sorted and filtered on the device (a library of a few thousand
 * items, read once by name). The sort, the filters and the Random order's seed live in the saved state, so they stay
 * while the app runs and a page opened from the grid comes back to the same grid.
 */
abstract class TvVideoGridViewModel(
    private val savedState: SavedStateHandle,
    private val sync: LibrarySyncRepository,
    val kind: TvVideoKind,
    posters: Flow<List<TvPoster>>,
    defaultDispatcher: CoroutineDispatcher,
    /** The page's name when it is not the kind's (a collection's). */
    title: Flow<String?> = flowOf(null),
) : ViewModel() {
    private val sort = savedState.getStateFlow(KEY_SORT, kind.sorts.first())
    private val watched = savedState.getStateFlow(KEY_WATCHED, TvWatched.All)
    private val favorites = savedState.getStateFlow(KEY_FAVORITES, false)
    private val genres = savedState.getStateFlow(KEY_GENRES, arrayListOf<String>())
    private val decades = savedState.getStateFlow(KEY_DECADES, arrayListOf<Int>())
    private val seed = savedState.getStateFlow(KEY_SEED, 0L)

    private val filter =
        combine(watched, favorites, genres, decades) { w, f, g, d -> TvVideoFilter(w, f, g.toSet(), d.toSet()) }

    val uiState: StateFlow<TvVideoGridUiState> =
        combine(posters, sort, filter, seed, title) { all, s, f, r, name ->
            if (all.isEmpty()) {
                TvVideoGridUiState.Empty
            } else {
                val arranged = arrange(all, s, f, r)
                TvVideoGridUiState.Content(
                    posters = arranged.posters,
                    letters = arranged.letters,
                    sort = s,
                    filter = f,
                    genres = genresOf(all),
                    decades = decadesOf(all),
                    total = all.size,
                    seed = r,
                    title = name,
                )
            }
        }
            // A few thousand posters mapped and sorted again on every library write: off the main thread.
            .flowOn(defaultDispatcher)
            // A database read failed: the next change reads again.
            .catch { emit(TvVideoGridUiState.Error) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvVideoGridUiState.Loading)

    fun onAction(action: TvVideoGridAction) {
        when (action) {
            is TvVideoGridAction.SetSort -> {
                // Random again reshuffles.
                if (action.sort == TvVideoSort.Random) savedState[KEY_SEED] = Random.nextLong()
                savedState[KEY_SORT] = action.sort
            }

            is TvVideoGridAction.SetWatched -> {
                savedState[KEY_WATCHED] = action.watched
            }

            TvVideoGridAction.ToggleFavorites -> {
                savedState[KEY_FAVORITES] = !favorites.value
            }

            // Any clears the list; a choice turns on or off.
            is TvVideoGridAction.ToggleGenre -> {
                savedState[KEY_GENRES] = toggled(genres.value, action.genre)
            }

            is TvVideoGridAction.ToggleDecade -> {
                savedState[KEY_DECADES] = toggled(decades.value, action.decade)
            }

            TvVideoGridAction.ClearFilters -> {
                savedState[KEY_WATCHED] = TvWatched.All
                savedState[KEY_FAVORITES] = false
                savedState[KEY_GENRES] = arrayListOf<String>()
                savedState[KEY_DECADES] = arrayListOf<Int>()
            }

            // The sync runs in its own job: leaving the screen does not stop it.
            TvVideoGridAction.Sync -> {
                viewModelScope.launch { sync.sync() }
            }
        }
    }

    /** [value] added to or removed from [list]; `null` empties it. An ArrayList: what the saved state keeps. */
    private fun <T> toggled(
        list: List<T>,
        value: T?,
    ): ArrayList<T> =
        when (value) {
            null -> arrayListOf()
            in list -> ArrayList(list - value)
            else -> ArrayList(list + value)
        }

    private companion object {
        const val KEY_SORT = "sort"
        const val KEY_WATCHED = "watched"
        const val KEY_FAVORITES = "favorites"
        const val KEY_GENRES = "genres"
        const val KEY_DECADES = "decades"
        const val KEY_SEED = "seed"
    }
}

/** Which grid, with the sorts it offers (the first is its default). */
enum class TvVideoKind(
    val sorts: List<TvVideoSort>,
    /** Whether the Filter button shows (watched, favorites, genre, decade). */
    val filters: Boolean = true,
) {
    Movies(
        listOf(
            TvVideoSort.Name,
            TvVideoSort.DateAdded,
            TvVideoSort.Year,
            TvVideoSort.Rating,
            TvVideoSort.Runtime,
            TvVideoSort.Random,
        ),
    ),
    Series(listOf(TvVideoSort.Name, TvVideoSort.DateAdded, TvVideoSort.Year, TvVideoSort.Rating, TvVideoSort.Random)),
    Collections(listOf(TvVideoSort.Name, TvVideoSort.DateAdded, TvVideoSort.Year, TvVideoSort.Random), filters = false),
    Collection(
        listOf(TvVideoSort.Collection, TvVideoSort.Name, TvVideoSort.Year, TvVideoSort.Rating, TvVideoSort.Runtime),
    ),
}

/** A kind's posters (#426), all of them, read once by name. */
private fun VideoRepository.posters(kind: TvVideoKind): Flow<List<TvPoster>> =
    if (kind == TvVideoKind.Series) {
        series(VideoSort.Name, unwatched = false).map { series -> series.map { it.toPoster() } }
    } else {
        movies(VideoSort.Name, unwatched = false).map { movies -> movies.map { it.toPoster() } }
    }

/**
 * The Movies or Series grid (#426) of one tab or page (#522): every poster, the favorites, or one genre's or studio's
 * ([only]), which then sort and filter as usual; a genre or studio page is titled with its name.
 */
@HiltViewModel(assistedFactory = TvVideosViewModel.Factory::class)
class TvVideosViewModel
    @AssistedInject
    constructor(
        @Assisted kind: TvVideoKind,
        @Assisted val only: TvVideoOnly,
        savedState: SavedStateHandle,
        sync: LibrarySyncRepository,
        videos: VideoRepository,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : TvVideoGridViewModel(
            savedState,
            sync,
            kind,
            videos.posters(kind).map { posters -> posters.filter(only::keeps) },
            defaultDispatcher,
            flowOf(
                when (only) {
                    is TvVideoOnly.Genre -> only.name
                    is TvVideoOnly.Studio -> only.name
                    else -> null
                },
            ),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(
                kind: TvVideoKind,
                only: TvVideoOnly,
            ): TvVideosViewModel
        }
    }

/** The Genres or Studios / Networks tab (#522): a card per genre or studio of the kind's posters. */
@HiltViewModel(assistedFactory = TvVideoGroupsViewModel.Factory::class)
class TvVideoGroupsViewModel
    @AssistedInject
    constructor(
        @Assisted kind: TvVideoKind,
        @Assisted studios: Boolean,
        videos: VideoRepository,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        val groups: StateFlow<TvVideoGroupsUiState> =
            videos
                .posters(kind)
                .map<_, TvVideoGroupsUiState> { posters ->
                    val groups = groupsOf(posters) { if (studios) it.studios else it.genres }
                    // A studio shows its own picture with its logo (#576), not a poster.
                    TvVideoGroupsUiState.Groups(
                        if (studios) groups.map { it.copy(artwork = studioThumb(it.name)) } else groups,
                    )
                }.flowOn(defaultDispatcher)
                // A database read failed: the next change reads again.
                .catch { emit(TvVideoGroupsUiState.Error) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvVideoGroupsUiState.Loading)

        @AssistedFactory
        interface Factory {
            fun create(
                kind: TvVideoKind,
                studios: Boolean,
            ): TvVideoGroupsViewModel
        }
    }

@HiltViewModel
class TvCollectionsViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        sync: LibrarySyncRepository,
        videos: VideoRepository,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : TvVideoGridViewModel(
            savedState,
            sync,
            TvVideoKind.Collections,
            videos.collections().map { collections -> collections.map { it.toPoster() } },
            defaultDispatcher,
        )

/** One collection's movies (#426), in the collection's order first. */
@HiltViewModel(assistedFactory = TvCollectionViewModel.Factory::class)
class TvCollectionViewModel
    @AssistedInject
    constructor(
        @Assisted id: MediaId,
        savedState: SavedStateHandle,
        sync: LibrarySyncRepository,
        videos: VideoRepository,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : TvVideoGridViewModel(
            savedState,
            sync,
            TvVideoKind.Collection,
            videos.collectionMovies(id).map { movies -> movies.map { it.toPoster() } },
            defaultDispatcher,
            videos.collection(id).map { it?.title },
        ) {
        @AssistedFactory
        interface Factory {
            fun create(id: MediaId): TvCollectionViewModel
        }
    }

sealed interface TvVideoGroupsUiState {
    data object Loading : TvVideoGroupsUiState

    data object Error : TvVideoGroupsUiState

    data class Groups(
        val groups: List<TvVideoGroup>,
    ) : TvVideoGroupsUiState
}

sealed interface TvVideoGridUiState {
    data object Loading : TvVideoGridUiState

    /** Nothing of this kind synced (yet). */
    data object Empty : TvVideoGridUiState

    data object Error : TvVideoGridUiState

    /** [posters] may be empty when the filters keep nothing; [total] counts the grid before them. */
    data class Content(
        val posters: List<TvPoster>,
        val letters: List<Letter>,
        val sort: TvVideoSort,
        val filter: TvVideoFilter,
        val genres: List<String>,
        val decades: List<Int>,
        val total: Int,
        /** Random's shuffle: a new one starts the grid at the top again. */
        val seed: Long = 0,
        /** A collection's name; `null` on the kind's own grid. */
        val title: String? = null,
    ) : TvVideoGridUiState
}

sealed interface TvVideoGridAction {
    data class SetSort(
        val sort: TvVideoSort,
    ) : TvVideoGridAction

    data class SetWatched(
        val watched: TvWatched,
    ) : TvVideoGridAction

    data object ToggleFavorites : TvVideoGridAction

    /** Turns [genre] on or off; `null` (Any) clears the genres. */
    data class ToggleGenre(
        val genre: String?,
    ) : TvVideoGridAction

    /** Turns [decade] on or off; `null` (Any) clears the decades. */
    data class ToggleDecade(
        val decade: Int?,
    ) : TvVideoGridAction

    data object ClearFilters : TvVideoGridAction

    /** Retry from the empty and error states. */
    data object Sync : TvVideoGridAction
}
