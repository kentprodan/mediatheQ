package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.data.PlaylistRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [PlaylistRepository] in memory, for ViewModel tests: edits change [playlists] and [items] at once, are recorded in
 * [calls] (e.g. "remove:p1:e1,e2") and answer with [failure] when it is set (then nothing changes).
 */
class FakePlaylistRepository : PlaylistRepository {
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val items = MutableStateFlow<Map<MediaId, List<PlaylistItem>>>(emptyMap())
    val calls = mutableListOf<String>()
    var failure: MqError? = null

    /** The id [create] gives the new playlist. */
    var createdId: MediaId? = null

    override fun playlist(id: MediaId): Flow<Playlist?> = playlists.map { all -> all.firstOrNull { it.id == id } }

    override fun items(id: MediaId): Flow<List<PlaylistItem>> = items.map { it[id].orEmpty() }

    override suspend fun create(
        name: String,
        trackIds: List<MediaId>,
    ): MqResult<MediaId> {
        calls += "create:$name:${trackIds.joinToString(",") { it.itemId }}"
        failure?.let { return MqResult.Failure(it) }
        val id = checkNotNull(createdId) { "set createdId first" }
        playlists.value += Playlist(id, name, trackCount = trackIds.size)
        return MqResult.Success(id)
    }

    override suspend fun rename(
        id: MediaId,
        name: String,
    ) = edit("rename:${id.itemId}:$name") {
        playlists.value = playlists.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun delete(id: MediaId) =
        edit("delete:${id.itemId}") { playlists.value = playlists.value.filterNot { it.id == id } }

    override suspend fun add(
        id: MediaId,
        trackIds: List<MediaId>,
    ) = edit("add:${id.itemId}:${trackIds.joinToString(",") { it.itemId }}") {}

    override suspend fun remove(
        id: MediaId,
        entryIds: Set<String>,
    ) = edit("remove:${id.itemId}:${entryIds.sorted().joinToString(",")}") {
        items.value += id to items.value[id].orEmpty().filterNot { it.entryId in entryIds }
    }

    override suspend fun move(
        id: MediaId,
        item: PlaylistItem,
        target: PlaylistItem,
    ) = edit("move:${id.itemId}:${item.entryId}:${target.position}") {
        val list = items.value[id].orEmpty().toMutableList()
        list.add(list.indexOf(target), list.removeAt(list.indexOf(item)))
        items.value += id to list
    }

    private fun edit(
        call: String,
        change: () -> Unit,
    ): MqResult<Unit> {
        calls += call
        failure?.let { return MqResult.Failure(it) }
        change()
        return MqResult.Success(Unit)
    }
}
