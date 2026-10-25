package io.github.kentprodan.mediatheq.provider.jellyfin

import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import io.github.kentprodan.mediatheq.core.server.UserDataSink
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.UpdateUserItemDataDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * [UserDataSink] for Jellyfin 12 (jellyfin skill, section 7). Favorite and rating both go through
 * `POST /UserItems/{itemId}/UserData`, which changes only the fields sent. `/UserItems/{itemId}/Rating` is the
 * like/dislike flag, not the rating, so it is not used. The server only applies a rating that has a value: clearing
 * sends 0, which the mapper reads back as unrated (R14).
 */
class JellyfinUserDataSink internal constructor(
    private val http: JellyfinHttp,
) : UserDataSink {
    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        clientInfo: ClientInfo,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(JellyfinHttp(okHttpClient, clientInfo, json, ioDispatcher))

    override suspend fun setFavorite(
        server: SignedInServer,
        itemId: MediaId,
        isFavorite: Boolean,
    ) = update(server, itemId, UpdateUserItemDataDto(isFavorite = isFavorite))

    override suspend fun setRating(
        server: SignedInServer,
        itemId: MediaId,
        rating: Double?,
    ) = update(server, itemId, UpdateUserItemDataDto(rating = rating ?: UNRATED))

    private suspend fun update(
        server: SignedInServer,
        itemId: MediaId,
        body: UpdateUserItemDataDto,
    ): MqResult<Unit> =
        http.post(
            server.connection,
            "UserItems/${itemId.itemId}/UserData",
            http.jsonBody(UpdateUserItemDataDto.serializer(), body),
            listOf("userId" to server.userId),
        )

    internal companion object {
        /** The rating the server stores for "no rating" (R14). */
        const val UNRATED = 0.0
    }
}
