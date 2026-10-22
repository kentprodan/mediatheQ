package io.github.kentprodan.mediatheq.feature.playlist

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist

sealed interface AddToPlaylistUiState {
    data object Loading : AddToPlaylistUiState

    data class Content(
        val playlists: List<Playlist>,
        /** Naming a new playlist instead of picking one (always when there are no tracks to add). */
        val creating: Boolean = false,
        /** A request is running; the buttons wait. */
        val busy: Boolean = false,
        val error: MqError? = null,
    ) : AddToPlaylistUiState

    /** The tracks were added (or the playlist created); the dialog closes. */
    data object Done : AddToPlaylistUiState
}

sealed interface AddToPlaylistAction {
    data class Pick(
        val playlistId: MediaId,
    ) : AddToPlaylistAction

    data object StartCreating : AddToPlaylistAction

    data class Create(
        val name: String,
    ) : AddToPlaylistAction
}
