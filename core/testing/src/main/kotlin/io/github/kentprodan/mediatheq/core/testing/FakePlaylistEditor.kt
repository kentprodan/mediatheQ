package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.server.PlaylistEditor
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.CompletableDeferred

/**
 * [PlaylistEditor] that records each call (e.g. "add:p1:t1,t2", "move:p1:e3:0") in [calls]. [failure] makes every call
 * fail; [gate], when set, suspends every call until it completes, to look at state while a request is in flight.
 */
class FakePlaylistEditor : PlaylistEditor {
    val calls = mutableListOf<String>()
    var failure: MqError? = null
    var gate: CompletableDeferred<Unit>? = null

    /** The id [create] returns. */
    var createdId: String = "new-playlist"

    override suspend fun create(
        server: SignedInServer,
        name: String,
    ): MqResult<MediaId> = call("create:$name") { MediaId(server.serverId, createdId) }

    override suspend fun rename(
        server: SignedInServer,
        playlistId: MediaId,
        name: String,
    ) = call("rename:${playlistId.itemId}:$name") {}

    override suspend fun deletePlaylist(
        server: SignedInServer,
        playlistId: MediaId,
    ) = call("delete:${playlistId.itemId}") {}

    override suspend fun add(
        server: SignedInServer,
        playlistId: MediaId,
        trackIds: List<MediaId>,
    ) = call("add:${playlistId.itemId}:${trackIds.joinToString(",") { it.itemId }}") {}

    override suspend fun remove(
        server: SignedInServer,
        playlistId: MediaId,
        entryIds: List<String>,
    ) = call("remove:${playlistId.itemId}:${entryIds.joinToString(",")}") {}

    override suspend fun move(
        server: SignedInServer,
        playlistId: MediaId,
        entryId: String,
        newIndex: Int,
    ) = call("move:${playlistId.itemId}:$entryId:$newIndex") {}

    private suspend fun <T> call(
        name: String,
        value: () -> T,
    ): MqResult<T> {
        calls += name
        gate?.await()
        return failure?.let { MqResult.Failure(it) } ?: MqResult.Success(value())
    }
}
