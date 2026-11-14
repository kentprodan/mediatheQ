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
import io.github.kentprodan.mediatheq.core.model.ResolvedTracks
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Video
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
        /** The episode whose tracks sheet is open, with its tracks (#274); `null` when closed. */
        val tracksOf: Pair<Video, VideoTracks>? = null,
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

    /** An episode's menu: Audio and subtitles (#274). */
    data class OpenTracks(
        val episode: Video,
    ) : SeriesAction

    data object CloseTracks : SeriesAction

    data class ChooseAudio(
        val index: Int,
    ) : SeriesAction

    /** `null` = Off. */
    data class ChooseSubtitle(
        val index: Int?,
    ) : SeriesAction

    /** The choice for the episode's whole season, or for the episode alone. */
    data class WholeSeason(
        val on: Boolean,
    ) : SeriesAction
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
        private val picker: TrackPicker,
    ) : ViewModel() {
        private val chosenSeason = MutableStateFlow<MediaId?>(null)
        private val tracksEpisode = MutableStateFlow<Video?>(null)
        private val tracks =
            tracksEpisode.flatMapLatest { episode ->
                episode?.let { picker.tracks(it).map { tracks -> episode to tracks } } ?: flowOf(null)
            }
        private val nextUp = videos.nextUp(id)

        // Next up's season when the page opened: decided once, so marking an episode watched never switches the tab.
        private var openedOn: MediaId? = null

        /** The chosen tab, else the season the page opened on (Next up's, else the first). */
        private val season =
            combine(videos.seasons(id), chosenSeason, nextUp) { seasons, chosen, next ->
                if (openedOn == null && seasons.isNotEmpty()) {
                    openedOn = (seasons.firstOrNull { it.id == next?.seasonId } ?: seasons.first()).id
                }
                seasons to (seasons.firstOrNull { it.id == chosen } ?: seasons.firstOrNull { it.id == openedOn })
            }.distinctUntilChanged()

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
                tracks,
            ) { series, (seasons, season, episodes), next, tracks ->
                if (series == null) {
                    SeriesUiState.Error(MqError.NotFound)
                } else {
                    SeriesUiState.Content(series, seasons, season, episodes, next, tracks)
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

                is SeriesAction.OpenTracks -> {
                    tracksEpisode.value = action.episode
                }

                SeriesAction.CloseTracks -> {
                    tracksEpisode.value = null
                }

                is SeriesAction.ChooseAudio -> {
                    choose { tracks, _ -> tracks.copy(audioIndex = action.index) }
                }

                is SeriesAction.ChooseSubtitle -> {
                    choose { tracks, _ -> tracks.copy(subtitleIndex = action.index) }
                }

                is SeriesAction.WholeSeason -> {
                    choose(wholeSeason = action.on) { tracks, _ -> tracks }
                }
            }
        }

        /** Saves the open episode's tracks as [change] makes them, for it or (with [wholeSeason]) its season. */
        private fun choose(
            wholeSeason: Boolean? = null,
            change: (ResolvedTracks, Video) -> ResolvedTracks,
        ) {
            val (episode, tracks) = (uiState.value as? SeriesUiState.Content)?.tracksOf ?: return
            val chosen = change(tracks.resolved, episode)
            viewModelScope.launch {
                picker.choose(
                    episode,
                    chosen.audioIndex,
                    chosen.subtitleIndex,
                    wholeSeason ?: (tracks.wholeSeason == true),
                )
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(id: MediaId): SeriesViewModel
        }
    }
