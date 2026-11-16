package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.ui.tv.Letter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
) : ViewModel() {
    private val sort = savedState.getStateFlow(KEY_SORT, TvVideoSort.Name)
    private val watched = savedState.getStateFlow(KEY_WATCHED, TvWatched.All)
    private val favorites = savedState.getStateFlow(KEY_FAVORITES, false)
    private val genre = savedState.getStateFlow<String?>(KEY_GENRE, null)
    private val decade = savedState.getStateFlow<Int?>(KEY_DECADE, null)
    private val seed = savedState.getStateFlow(KEY_SEED, 0L)

    private val filter =
        combine(watched, favorites, genre, decade) { w, f, g, d -> TvVideoFilter(w, f, g, d) }

    val uiState: StateFlow<TvVideoGridUiState> =
        combine(posters, sort, filter, seed) { all, s, f, r ->
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

            is TvVideoGridAction.SetGenre -> {
                savedState[KEY_GENRE] = action.genre
            }

            is TvVideoGridAction.SetDecade -> {
                savedState[KEY_DECADE] = action.decade
            }

            TvVideoGridAction.ClearFilters -> {
                savedState[KEY_WATCHED] = TvWatched.All
                savedState[KEY_FAVORITES] = false
                savedState[KEY_GENRE] = null
                savedState[KEY_DECADE] = null
            }

            // The sync runs in its own job: leaving the screen does not stop it.
            TvVideoGridAction.Sync -> {
                viewModelScope.launch { sync.sync() }
            }
        }
    }

    private companion object {
        const val KEY_SORT = "sort"
        const val KEY_WATCHED = "watched"
        const val KEY_FAVORITES = "favorites"
        const val KEY_GENRE = "genre"
        const val KEY_DECADE = "decade"
        const val KEY_SEED = "seed"
    }
}

/** Which grid: Movies has the Runtime sort and the movie placeholder; Series the series one. */
enum class TvVideoKind { Movies, Series }

@HiltViewModel
class TvMoviesViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        sync: LibrarySyncRepository,
        videos: VideoRepository,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : TvVideoGridViewModel(
            savedState,
            sync,
            TvVideoKind.Movies,
            videos.movies(VideoSort.Name, unwatched = false).map { movies -> movies.map { it.toPoster() } },
            defaultDispatcher,
        )

@HiltViewModel
class TvSeriesViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        sync: LibrarySyncRepository,
        videos: VideoRepository,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : TvVideoGridViewModel(
            savedState,
            sync,
            TvVideoKind.Series,
            videos.series(VideoSort.Name, unwatched = false).map { series -> series.map { it.toPoster() } },
            defaultDispatcher,
        )

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

    data class SetGenre(
        val genre: String?,
    ) : TvVideoGridAction

    data class SetDecade(
        val decade: Int?,
    ) : TvVideoGridAction

    data object ClearFilters : TvVideoGridAction

    /** Retry from the empty and error states. */
    data object Sync : TvVideoGridAction
}
