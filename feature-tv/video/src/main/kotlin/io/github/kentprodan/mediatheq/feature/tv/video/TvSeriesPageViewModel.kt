package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.VideoPreferencesRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A series' page on TV (#428): seasons as tabs (Specials last), the selected season's episodes, Next up for the main
 * button, all from Room. The page opens on Next up's season; marking watched never switches the tab.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = TvSeriesPageViewModel.Factory::class)
class TvSeriesPageViewModel
    @AssistedInject
    constructor(
        @Assisted private val id: MediaId,
        private val videos: VideoRepository,
        private val userData: UserDataRepository,
        private val preferences: VideoPreferencesRepository,
    ) : ViewModel() {
        private val chosenSeason = MutableStateFlow<MediaId?>(null)
        private val nextUp = videos.nextUp(id)
        private val confirm = MutableStateFlow<TvWatchedTarget?>(null)

        // Next up's season when the page opened: decided once.
        private var openedOn: MediaId? = null

        private val season =
            combine(videos.seasons(id), chosenSeason, nextUp) { seasons, chosen, next ->
                val ordered = specialsLast(seasons)
                if (openedOn == null && ordered.isNotEmpty()) {
                    openedOn = (ordered.firstOrNull { it.id == next?.seasonId } ?: ordered.first()).id
                }
                ordered to (ordered.firstOrNull { it.id == chosen } ?: ordered.firstOrNull { it.id == openedOn })
            }.distinctUntilChanged()

        val uiState: StateFlow<TvSeriesPageUiState> =
            combine(
                videos.series(id),
                season.flatMapLatest { (seasons, selected) ->
                    (
                        selected?.let {
                            videos.episodes(
                                it.id,
                            )
                        } ?: flowOf(emptyList())
                    ).map { Triple(seasons, selected, it) }
                },
                combine(nextUp, videos.allSeriesEpisodes(id).map { specialsLastEpisodes(it).firstOrNull() }) { n, f ->
                    n to
                        f
                },
                preferences.preferences.map { it.hideUnwatchedOverviews }.distinctUntilChanged(),
                confirm,
            ) { series, (seasons, selected, episodes), (next, first), hide, asking ->
                if (series == null) {
                    TvSeriesPageUiState.Error(MqError.NotFound)
                } else {
                    TvSeriesPageUiState.Content(
                        series = series,
                        seasons = seasons,
                        season = selected,
                        episodes = episodes,
                        nextUp = next,
                        firstEpisode = first,
                        hideUnwatchedOverviews = hide,
                        confirm = asking,
                    )
                }
            }.catch { emit(TvSeriesPageUiState.Error(MqError.Storage)) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvSeriesPageUiState.Loading)

        fun onAction(action: TvSeriesPageAction) {
            val content = uiState.value as? TvSeriesPageUiState.Content ?: return
            when (action) {
                is TvSeriesPageAction.SelectSeason -> {
                    chosenSeason.value = action.id
                }

                TvSeriesPageAction.ToggleFavorite -> {
                    viewModelScope.launch { userData.setFavorite(id, !content.series.userData.isFavorite) }
                }

                is TvSeriesPageAction.ToggleEpisodeWatched -> {
                    viewModelScope.launch { userData.setPlayed(action.episode.id, !action.episode.userData.played) }
                }

                // Season and series ask first: they change many episodes at once.
                is TvSeriesPageAction.AskWatched -> {
                    confirm.value = action.target
                }

                TvSeriesPageAction.Dismiss -> {
                    confirm.value = null
                }

                TvSeriesPageAction.ConfirmWatched -> {
                    val target = confirm.value ?: return
                    confirm.value = null
                    viewModelScope.launch {
                        when (target) {
                            is TvWatchedTarget.Season -> userData.setPlayed(target.season.id, target.watched)
                            is TvWatchedTarget.Series -> userData.setPlayed(id, target.watched)
                        }
                    }
                }

                TvSeriesPageAction.ToggleHideOverviews -> {
                    viewModelScope.launch {
                        val hide = preferences.preferences.first().hideUnwatchedOverviews
                        preferences.update { it.copy(hideUnwatchedOverviews = !hide) }
                    }
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(id: MediaId): TvSeriesPageViewModel
        }
    }

/** Specials (season 0) go after the numbered seasons (owner, #428). */
internal fun specialsLast(seasons: List<Season>): List<Season> =
    seasons.filter { it.number != 0 } + seasons.filter { it.number == 0 }

/** The first numbered episode leads; specials (season 0) only when there is nothing else. */
internal fun specialsLastEpisodes(episodes: List<Video>): List<Video> =
    episodes.filter { it.seasonNumber != 0 } + episodes.filter { it.seasonNumber == 0 }

/** What a watched confirmation marks. */
sealed interface TvWatchedTarget {
    val watched: Boolean

    data class Season(
        val season: io.github.kentprodan.mediatheq.core.model.Season,
        override val watched: Boolean,
    ) : TvWatchedTarget

    data class Series(
        override val watched: Boolean,
    ) : TvWatchedTarget
}

sealed interface TvSeriesPageUiState {
    data object Loading : TvSeriesPageUiState

    data class Content(
        val series: Series,
        val seasons: List<Season>,
        val season: Season?,
        val episodes: List<Video>,
        /** The episode to watch next; `null` when all are watched. */
        val nextUp: Video?,
        /** Where From the start begins: the first episode, specials aside. */
        val firstEpisode: Video? = null,
        /** No spoilers: unwatched episodes show no overview. */
        val hideUnwatchedOverviews: Boolean = false,
        /** The watched change waiting for a yes. */
        val confirm: TvWatchedTarget? = null,
    ) : TvSeriesPageUiState

    data class Error(
        val error: MqError,
    ) : TvSeriesPageUiState
}

sealed interface TvSeriesPageAction {
    data class SelectSeason(
        val id: MediaId,
    ) : TvSeriesPageAction

    data object ToggleFavorite : TvSeriesPageAction

    data class ToggleEpisodeWatched(
        val episode: Video,
    ) : TvSeriesPageAction

    data class AskWatched(
        val target: TvWatchedTarget,
    ) : TvSeriesPageAction

    data object ConfirmWatched : TvSeriesPageAction

    data object Dismiss : TvSeriesPageAction

    data object ToggleHideOverviews : TvSeriesPageAction
}
