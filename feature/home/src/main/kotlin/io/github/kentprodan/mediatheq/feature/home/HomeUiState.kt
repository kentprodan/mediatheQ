package io.github.kentprodan.mediatheq.feature.home

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Track

/** What the Home tab shows. */
sealed interface HomeUiState {
    /** The local library is being read (a few milliseconds). */
    data object Loading : HomeUiState

    /** Nothing on the phone yet and the first sync is running. */
    data object FirstSync : HomeUiState

    /** The library has music; each list is one section, and an empty list hides its section. */
    data class Content(
        val recentlyPlayed: List<Album> = emptyList(),
        val recentlyAdded: List<Album> = emptyList(),
        val favoriteAlbums: List<Album> = emptyList(),
        val favoriteTracks: List<Track> = emptyList(),
    ) : HomeUiState

    /** Synced, but the server has no music. */
    data object Empty : HomeUiState

    /** Offline mode, and nothing for Home is on the phone (#63). */
    data object NothingDownloaded : HomeUiState

    /** Nothing on the phone yet and the first sync failed ([MqError.Network]: the server cannot be reached). */
    data class Error(
        val error: MqError,
    ) : HomeUiState
}

sealed interface HomeAction {
    /** Plays the whole library shuffled. */
    data object ShuffleAll : HomeAction

    /** Plays all favorite songs, starting at [track]. */
    data class PlayFavorite(
        val track: Track,
    ) : HomeAction

    data class PlayNext(
        val track: Track,
    ) : HomeAction

    data class AddToQueue(
        val track: Track,
    ) : HomeAction

    data class SetFavorite(
        val track: Track,
        val isFavorite: Boolean,
    ) : HomeAction

    data class Download(
        val track: Track,
    ) : HomeAction

    /** Sync now, from the empty and error states. */
    data object Sync : HomeAction
}
