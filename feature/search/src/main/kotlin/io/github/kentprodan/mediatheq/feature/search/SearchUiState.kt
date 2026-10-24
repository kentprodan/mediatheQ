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

    data class AddToQueue(
        val track: Track,
    ) : SearchAction

    data object ClearRecent : SearchAction

    /** Runs the failed search again. */
    data object Retry : SearchAction
}
