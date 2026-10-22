package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * One playlist and its edits. Edits show in Room at once, then go to the server; a failed edit is undone and returned
 * as a failure. After each edit the playlist is read back from the server (new entry ids, the R6 collage).
 */
interface PlaylistRepository {
    /** `null` when the playlist is not (or no longer) in the library. */
    fun playlist(id: MediaId): Flow<Playlist?>

    /** The entries in playlist order; entries whose track is not in the library are left out. */
    fun items(id: MediaId): Flow<List<PlaylistItem>>

    /** Creates an empty playlist named [name] and returns its id; tracks follow with [add]. */
    suspend fun create(name: String): MqResult<MediaId>

    /** Reads the playlist again from the server (e.g. when its screen opens). */
    suspend fun refresh(id: MediaId): MqResult<Unit>

    suspend fun rename(
        id: MediaId,
        name: String,
    ): MqResult<Unit>

    /** Deletes the playlist (only ever a playlist: other ids fail with `NotFound` without asking the server, R5). */
    suspend fun delete(id: MediaId): MqResult<Unit>

    /** Appends [trackIds] in order. */
    suspend fun add(
        id: MediaId,
        trackIds: List<MediaId>,
    ): MqResult<Unit>

    /** Removes the entries with [entryIds]; on Jellyfin 12.2 that is every copy of their track (R13). */
    suspend fun remove(
        id: MediaId,
        entryIds: Set<String>,
    ): MqResult<Unit>

    /** Moves [item] to where [target] is now, as in a drag and drop. */
    suspend fun move(
        id: MediaId,
        item: PlaylistItem,
        target: PlaylistItem,
    ): MqResult<Unit>
}

/**
 * One entry of a playlist. [position] counts every entry the server has (hidden ones too). A [isPending] entry was just
 * added and has no server entry id yet: it cannot be moved or removed until the playlist is read back.
 */
data class PlaylistItem(
    val entryId: String,
    val position: Int,
    val track: Track,
) {
    val isPending: Boolean get() = entryId == PENDING_ENTRY_ID

    companion object {
        /** The entry id of rows added locally and not read back from the server yet. */
        const val PENDING_ENTRY_ID = "pending"
    }
}
