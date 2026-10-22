package io.github.kentprodan.mediatheq.provider.jellyfin

import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.PlaylistEditor
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.CreatePlaylistDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.PlaylistCreationResultDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.UpdatePlaylistDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * [PlaylistEditor] for Jellyfin 12 (jellyfin skill, section 8). Id lists travel in the query string, so they go out in
 * chunks of [MAX_IDS] (R3). Creation always starts empty and tracks follow through [add]: one chunked path.
 */
class JellyfinPlaylistEditor internal constructor(
    private val http: JellyfinHttp,
) : PlaylistEditor {
    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        clientInfo: ClientInfo,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(JellyfinHttp(okHttpClient, clientInfo, json, ioDispatcher))

    override suspend fun create(
        server: SignedInServer,
        name: String,
    ): MqResult<MediaId> =
        http.post(
            server.connection,
            "Playlists",
            PlaylistCreationResultDto.serializer(),
            http.jsonBody(CreatePlaylistDto.serializer(), CreatePlaylistDto(name, server.userId, mediaType = "Audio")),
        ) { result -> result.id?.takeIf { it.isNotEmpty() }?.let { MediaId(server.serverId, it) } }

    override suspend fun rename(
        server: SignedInServer,
        playlistId: MediaId,
        name: String,
    ): MqResult<Unit> =
        http.post(
            server.connection,
            "Playlists/${playlistId.itemId}",
            http.jsonBody(UpdatePlaylistDto.serializer(), UpdatePlaylistDto(name = name)),
        )

    // R5: the only DELETE /Items call in the app, and only for a playlist (the repository checks the id first).
    override suspend fun deletePlaylist(
        server: SignedInServer,
        playlistId: MediaId,
    ): MqResult<Unit> = http.delete(server.connection, "Items/${playlistId.itemId}")

    override suspend fun add(
        server: SignedInServer,
        playlistId: MediaId,
        trackIds: List<MediaId>,
    ): MqResult<Unit> =
        inChunks(trackIds.map { it.itemId }) { ids ->
            http.post(
                server.connection,
                "Playlists/${playlistId.itemId}/Items",
                query = listOf("ids" to ids.joinToString(","), "userId" to server.userId),
            )
        }

    override suspend fun remove(
        server: SignedInServer,
        playlistId: MediaId,
        entryIds: List<String>,
    ): MqResult<Unit> =
        inChunks(entryIds) { ids ->
            http.delete(
                server.connection,
                "Playlists/${playlistId.itemId}/Items",
                listOf("entryIds" to ids.joinToString(",")),
            )
        }

    override suspend fun move(
        server: SignedInServer,
        playlistId: MediaId,
        entryId: String,
        newIndex: Int,
    ): MqResult<Unit> = http.post(server.connection, "Playlists/${playlistId.itemId}/Items/$entryId/Move/$newIndex")

    /** Sends [ids] in order, [MAX_IDS] at a time, stopping at the first failure. */
    private suspend fun inChunks(
        ids: List<String>,
        send: suspend (List<String>) -> MqResult<Unit>,
    ): MqResult<Unit> {
        for (chunk in ids.chunked(MAX_IDS)) {
            val result = send(chunk)
            if (result is MqResult.Failure) return result
        }
        return MqResult.Success(Unit)
    }

    private companion object {
        // shortcut: fixed Jellyfin limit (R3); move it to ServerCapabilities when a second provider needs another one.
        const val MAX_IDS = 200
    }
}
