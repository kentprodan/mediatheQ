package io.github.kentprodan.mediatheq.provider.jellyfin

import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.LyricsSource
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.LyricDto
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject

/** [LyricsSource] for Jellyfin 12 (jellyfin skill, section 10). The server answers 404 for a track without lyrics. */
class JellyfinLyricsSource internal constructor(
    private val http: JellyfinHttp,
) : LyricsSource {
    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        clientInfo: ClientInfo,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(JellyfinHttp(okHttpClient, clientInfo, json, ioDispatcher))

    override suspend fun lyrics(
        server: SignedInServer,
        trackId: MediaId,
    ): MqResult<Lyrics?> =
        when (
            val result =
                http.get(server.connection, "Audio/${trackId.itemId}/Lyrics", LyricDto.serializer()) {
                    it.toDomain()
                }
        ) {
            is MqResult.Success -> MqResult.Success(result.value.takeIf { it.lines.isNotEmpty() })
            is MqResult.Failure -> if (result.error == MqError.NotFound) MqResult.Success(null) else result
        }
}
