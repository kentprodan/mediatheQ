package io.github.kentprodan.mediatheq.feature.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * A poster grid (#270): its items in the chosen sort, optionally unwatched only, in the album grid's columns + 1
 * (posters are narrower than covers). Sort and filter live in the saved state; a saved sort per list is a follow-up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class VideoGridViewModel(
    private val savedState: SavedStateHandle,
    preferences: LibraryPreferencesRepository,
    items: (VideoSort, Boolean) -> Flow<List<PosterItem>>,
) : ViewModel() {
    private val sort = savedState.getStateFlow(KEY_SORT, VideoSort.Name)
    private val unwatched = savedState.getStateFlow(KEY_UNWATCHED, false)

    val uiState: StateFlow<VideoGridUiState> =
        combine(
            sort,
            unwatched,
            preferences.preferences.map { it.albumColumns + 1 }.distinctUntilChanged(),
        ) { s, u, c ->
            Triple(s, u, c)
        }.flatMapLatest { (s, u, columns) ->
            items(s, u)
                .map<_, VideoGridUiState> { VideoGridUiState.Content(it, s, u, columns) }
                // A database read failed: nothing to retry from the screen, the next change reads again.
                .catch { emit(VideoGridUiState.Error) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoGridUiState.Loading)

    fun onAction(action: VideoGridAction) {
        when (action) {
            is VideoGridAction.SetSort -> savedState[KEY_SORT] = action.sort
            is VideoGridAction.SetUnwatched -> savedState[KEY_UNWATCHED] = action.unwatched
        }
    }

    private companion object {
        const val KEY_SORT = "sort"
        const val KEY_UNWATCHED = "unwatched"
    }
}

@HiltViewModel
class MoviesViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        preferences: LibraryPreferencesRepository,
        videos: VideoRepository,
    ) : VideoGridViewModel(
            savedState,
            preferences,
            { sort, unwatched ->
                videos.movies(sort, unwatched).map { movies ->
                    movies.map { PosterItem(it.id, it.title, it.year?.toString(), it.artwork, it.userData.played) }
                }
            },
        )

@HiltViewModel
class SeriesListViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        preferences: LibraryPreferencesRepository,
        videos: VideoRepository,
    ) : VideoGridViewModel(
            savedState,
            preferences,
            { sort, unwatched ->
                videos.series(sort, unwatched).map { series ->
                    series.map {
                        PosterItem(
                            it.id,
                            it.title,
                            it.year?.toString(),
                            it.artwork,
                            it.userData.played,
                            it.userData.unplayedCount,
                        )
                    }
                }
            },
        )

/** One poster: what the grid needs of a movie or a series. */
data class PosterItem(
    val id: MediaId,
    val title: String,
    val subtitle: String?,
    val artwork: Artwork?,
    val watched: Boolean,
    val unplayedCount: Int? = null,
)

sealed interface VideoGridUiState {
    data object Loading : VideoGridUiState

    /** [items] may be empty: nothing synced, or nothing unwatched. */
    data class Content(
        val items: List<PosterItem>,
        val sort: VideoSort,
        val unwatched: Boolean,
        val columns: Int,
    ) : VideoGridUiState

    data object Error : VideoGridUiState
}

sealed interface VideoGridAction {
    data class SetSort(
        val sort: VideoSort,
    ) : VideoGridAction

    data class SetUnwatched(
        val unwatched: Boolean,
    ) : VideoGridAction
}
