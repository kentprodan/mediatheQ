package io.github.kentprodan.mediatheq.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Video
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A series' page (#273). */
sealed interface SeriesUiState {
    data object Loading : SeriesUiState

    data class Content(
        val series: Series,
        val seasons: List<Season>,
        /** The season whose tab is selected. */
        val season: Season?,
        /** Its episodes in order; empty for a series without episodes yet. */
        val episodes: List<Video>,
        /** The episode to watch next (`nextUp`); `null` when all are watched. */
        val nextUp: Video?,
    ) : SeriesUiState

    data class Error(
        val error: MqError,
    ) : SeriesUiState
}

sealed interface SeriesAction {
    data class SelectSeason(
        val id: MediaId,
    ) : SeriesAction

    data class ToggleWatched(
        val episode: Video,
    ) : SeriesAction

    data object ToggleFavorite : SeriesAction
}

/**
 * A series' page (#273): its seasons as tabs (the Next up episode's season first selected), the selected season's
 * episodes and Next up, all from Room; watched and favorite through the optimistic user data writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = SeriesViewModel.Factory::class)
class SeriesViewModel
    @AssistedInject
    constructor(
        @Assisted private val id: MediaId,
        private val videos: VideoRepository,
        private val userData: UserDataRepository,
    ) : ViewModel() {
        private val chosenSeason = MutableStateFlow<MediaId?>(null)
        private val nextUp = videos.nextUp(id)

        /** The chosen tab, else Next up's season, else the first. */
        private val season =
            combine(videos.seasons(id), chosenSeason, nextUp) { seasons, chosen, next ->
                seasons to
                    (
                        seasons.firstOrNull { it.id == chosen }
                            ?: seasons.firstOrNull { it.id == next?.seasonId }
                            ?: seasons.firstOrNull()
                    )
            }

        val uiState: StateFlow<SeriesUiState> =
            combine(
                videos.series(id),
                season.flatMapLatest { (seasons, season) ->
                    (season?.let { videos.episodes(it.id) } ?: flowOf(emptyList())).let { episodes ->
                        combine(
                            episodes,
                            flowOf(seasons to season),
                        ) { list, pair -> Triple(pair.first, pair.second, list) }
                    }
                },
                nextUp,
            ) { series, (seasons, season, episodes), next ->
                if (series == null) {
                    SeriesUiState.Error(MqError.NotFound)
                } else {
                    SeriesUiState.Content(series, seasons, season, episodes, next)
                }
            }.catch { emit(SeriesUiState.Error(MqError.Storage)) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesUiState.Loading)

        fun onAction(action: SeriesAction) {
            when (action) {
                is SeriesAction.SelectSeason -> {
                    chosenSeason.value = action.id
                }

                is SeriesAction.ToggleWatched -> {
                    viewModelScope.launch { userData.setPlayed(action.episode.id, !action.episode.userData.played) }
                }

                SeriesAction.ToggleFavorite -> {
                    val series = (uiState.value as? SeriesUiState.Content)?.series ?: return
                    viewModelScope.launch { userData.setFavorite(id, !series.userData.isFavorite) }
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(id: MediaId): SeriesViewModel
        }
    }
