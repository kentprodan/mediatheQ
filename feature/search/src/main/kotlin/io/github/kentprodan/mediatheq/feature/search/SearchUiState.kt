package io.github.kentprodan.mediatheq.feature.search

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.SearchResults
import io.github.kentprodan.mediatheq.core.model.Track

/** What the Search tab shows below the search field. */
sealed interface SearchUiState {
    data object Loading : SearchUiState

    /** Nothing typed: the recent searches, newest first (a hint when there are none). */
    data class Recent(
        val searches: List<String>,
    ) : SearchUiState

    data class Results(
        val results: SearchResults,
    ) : SearchUiState

    data class NoResults(
        val query: String,
        /** Offline mode: only downloads were searched (#63). */
        val offline: Boolean = false,
    ) : SearchUiState

    data class Error(
        val error: MqError,
    ) : SearchUiState
}

sealed interface SearchAction {
    /** The text in the search field changed. */
    data class QueryChanged(
        val query: String,
    ) : SearchAction

    /** The keyboard's search key: keeps the query as a recent search. */
    data object Submit : SearchAction

    /** An artist, album or playlist result was opened: keeps the query as a recent search. */
    data object ResultOpened : SearchAction

    /** Plays only [track] and keeps the query as a recent search. */
    data class PlayTrack(
        val track: Track,
    ) : SearchAction

    data class PlayNext(
        val track: Track,
    ) : SearchAction

    data class Download(
        val track: Track,
    ) : SearchAction

    data class AddToQueue(
        val track: Track,
    ) : SearchAction

    data class SetFavorite(
        val track: Track,
        val isFavorite: Boolean,
    ) : SearchAction

    data object ClearRecent : SearchAction

    /** Runs the failed search again. */
    data object Retry : SearchAction

    /** A swipe on a track to the right (or left): the action chosen in Settings -> Media lists (#123). */
    data class SwipeTrack(
        val track: Track,
        val right: Boolean,
    ) : SearchAction

    /** Rates a track (a Rate swipe): [halfStars] 1-10, `null` clears. */
    data class RateTrack(
        val track: Track,
        val halfStars: Int?,
    ) : SearchAction
}
