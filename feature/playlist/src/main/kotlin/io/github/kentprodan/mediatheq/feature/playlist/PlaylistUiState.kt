package io.github.kentprodan.mediatheq.feature.playlist

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.PlaylistButton
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.data.PlaylistPagePreferences
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ResumePoint
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
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
        val page: PlaylistPagePreferences = PlaylistPagePreferences(),
        /** Where Resume starts (#135); `null` hides it (never played from here, or finished). */
        val resume: ResumePoint? = null,
        /** Every track of the playlist is downloaded. */
        val isDownloaded: Boolean = false,
        /** More -> Edit: selection mode before anything is selected. */
        val editing: Boolean = false,
    ) : PlaylistUiState {
        val isSelecting: Boolean get() = editing || selected.isNotEmpty()

        /** Whether [button] shows in the header (else it is in More). */
        fun shows(button: PlaylistButton): Boolean = button !in page.hiddenButtons

        /** No moves or removals while just-added tracks wait for their server entry ids. */
        val canEdit: Boolean get() = items.none { it.isPending }

        val duration: Duration get() = items.fold(Duration.ZERO) { total, item -> total + item.track.duration }
    }
}

sealed interface PlaylistAction {
    data object Play : PlaylistAction

    data object Shuffle : PlaylistAction

    /** Plays on from where the playlist was left. */
    data object Resume : PlaylistAction

    /** The header's Play next / Add to queue: the whole playlist. */
    data object PlayAllNext : PlaylistAction

    data object AddAllToQueue : PlaylistAction

    /** Favorite or not: the playlist itself. */
    data class SetPlaylistFavorite(
        val isFavorite: Boolean,
    ) : PlaylistAction

    /** More -> Edit: starts selecting with nothing selected yet. */
    data object StartEditing : PlaylistAction

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

    /** Favorite or not: the track at [position]. */
    data class SetFavorite(
        val position: Int,
        val isFavorite: Boolean,
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

    data object SelectAll : PlaylistAction

    /** A selection toolbar action on the selected tracks (Add to playlist is the screen's); selecting ends (#123). */
    data class OnSelection(
        val command: SelectionCommand,
    ) : PlaylistAction

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

    /** A swipe on a track to the right (or left): the action chosen in Settings -> Media lists (#123). */
    data class SwipeTrack(
        val track: Track,
        val right: Boolean,
    ) : PlaylistAction

    /** Rates a track (a Rate swipe): [halfStars] 1-10, `null` clears. */
    data class RateTrack(
        val track: Track,
        val halfStars: Int?,
    ) : PlaylistAction
}
