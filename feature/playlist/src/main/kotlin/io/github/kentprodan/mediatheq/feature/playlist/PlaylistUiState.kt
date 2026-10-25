package io.github.kentprodan.mediatheq.feature.playlist

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import kotlin.time.Duration

sealed interface PlaylistUiState {
    /** Before the first database read; shows only the top bar. */
    data object Loading : PlaylistUiState

    /** The playlist is not in the library (deleted elsewhere, or a stale link). */
    data object NotFound : PlaylistUiState

    /** The user deleted it here; the screen closes. */
    data object Deleted : PlaylistUiState

    data class Content(
        val playlist: Playlist,
        val items: List<PlaylistItem>,
        val currentTrackId: MediaId? = null,
        /**
         * Entry ids of the selected items; selection mode while not empty. Ids, not positions: a read-back may
         * renumber the items. Copies of a track share their id and are selected together (R13).
         */
        val selected: Set<String> = emptySet(),
        /** The last edit that failed (it was undone), until the user dismisses it. */
        val error: MqError? = null,
    ) : PlaylistUiState {
        val isSelecting: Boolean get() = selected.isNotEmpty()

        /** No moves or removals while just-added tracks wait for their server entry ids. */
        val canEdit: Boolean get() = items.none { it.isPending }

        val duration: Duration get() = items.fold(Duration.ZERO) { total, item -> total + item.track.duration }
    }
}

sealed interface PlaylistAction {
    data object Play : PlaylistAction

    data object Shuffle : PlaylistAction

    /** Items are named by their [PlaylistItem.position]: the same track can be in a playlist twice. */
    data class PlayItem(
        val position: Int,
    ) : PlaylistAction

    data class PlayNext(
        val position: Int,
    ) : PlaylistAction

    data class AddToQueue(
        val position: Int,
    ) : PlaylistAction

    /** Download one item, or the whole playlist when [position] is `null`. */
    data class Download(
        val position: Int? = null,
    ) : PlaylistAction

    /** Starts selecting, or adds/removes an item from the selection. */
    data class ToggleSelection(
        val position: Int,
    ) : PlaylistAction

    data object ClearSelection : PlaylistAction

    data object RemoveSelected : PlaylistAction

    data class Remove(
        val position: Int,
    ) : PlaylistAction

    /** Drag and drop: the item at [position] goes where the item at [targetPosition] is. */
    data class Move(
        val position: Int,
        val targetPosition: Int,
    ) : PlaylistAction

    data class Rename(
        val name: String,
    ) : PlaylistAction

    data object Delete : PlaylistAction

    data object DismissError : PlaylistAction
}
