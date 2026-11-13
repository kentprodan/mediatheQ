package io.github.kentprodan.mediatheq.feature.tv.search

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.Track

/** Search on TV (#244). */
sealed interface TvSearchUiState {
    data object Loading : TvSearchUiState

    /** The field is empty: the recent searches, newest first. */
    data class Recent(
        val searches: List<String>,
    ) : TvSearchUiState

    /** What the query finds, as the four rows the TV shows. */
    data class Results(
        val albums: List<Album>,
        val artists: List<Artist>,
        val tracks: List<Track>,
        val playlists: List<Playlist>,
    ) : TvSearchUiState

    /** Nothing in the four rows matches (a genre or label alone does not count: the TV does not show them). */
    data object NoResults : TvSearchUiState

    data class Error(
        val error: MqError,
    ) : TvSearchUiState
}

sealed interface TvSearchAction {
    data class QueryChanged(
        val query: String,
    ) : TvSearchAction

    /** The keyboard's Search key: the query becomes a recent search. */
    data object Submit : TvSearchAction

    /** A recent search was selected: it fills the field and searches. */
    data class PickRecent(
        val query: String,
    ) : TvSearchAction

    /** Plays the Tracks row from [index]. */
    data class PlayTrack(
        val index: Int,
    ) : TvSearchAction

    /** A result was opened (album, artist, playlist): the query becomes a recent search. */
    data object Opened : TvSearchAction
}
