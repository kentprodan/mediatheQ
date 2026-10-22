package io.github.kentprodan.mediatheq.core.server

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId

/** Playlist writes. Providers split long id lists into as many requests as their server needs. */
interface PlaylistEditor {
    /** Creates an empty audio playlist named [name] and returns its id. */
    suspend fun create(
        server: SignedInServer,
        name: String,
    ): MqResult<MediaId>

    suspend fun rename(
        server: SignedInServer,
        playlistId: MediaId,
        name: String,
    ): MqResult<Unit>

    /** Deletes the playlist itself (never its tracks). Only ever call it with a playlist id. */
    suspend fun deletePlaylist(
        server: SignedInServer,
        playlistId: MediaId,
    ): MqResult<Unit>

    /** Appends [trackIds] in order. On a failure part of them may already have been added. */
    suspend fun add(
        server: SignedInServer,
        playlistId: MediaId,
        trackIds: List<MediaId>,
    ): MqResult<Unit>

    /** Removes positions by their playlist entry ids (not track ids). */
    suspend fun remove(
        server: SignedInServer,
        playlistId: MediaId,
        entryIds: List<String>,
    ): MqResult<Unit>

    /** Moves one entry to [newIndex], counted over all of the playlist's entries on the server. */
    suspend fun move(
        server: SignedInServer,
        playlistId: MediaId,
        entryId: String,
        newIndex: Int,
    ): MqResult<Unit>
}
