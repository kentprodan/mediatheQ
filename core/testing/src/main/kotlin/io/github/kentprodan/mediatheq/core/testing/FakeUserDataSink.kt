package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import io.github.kentprodan.mediatheq.core.server.UserDataSink
import kotlinx.coroutines.CompletableDeferred

/**
 * [UserDataSink] that keeps what a server would store in [serverState] and records each call (e.g. "favorite:t1:true",
 * "rating:t1:8.0") in [calls]. [failure] makes every call fail; [gate], when set, holds every call until it completes.
 */
class FakeUserDataSink : UserDataSink {
    val serverState = mutableMapOf<MediaId, UserData>()
    val calls = mutableListOf<String>()
    var failure: MqError? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun setFavorite(
        server: SignedInServer,
        itemId: MediaId,
        isFavorite: Boolean,
    ) = call("favorite:${itemId.itemId}:$isFavorite", itemId) { it.copy(isFavorite = isFavorite) }

    override suspend fun setRating(
        server: SignedInServer,
        itemId: MediaId,
        rating: Double?,
    ) = call("rating:${itemId.itemId}:$rating", itemId) { it.copy(rating = rating) }

    private suspend fun call(
        name: String,
        itemId: MediaId,
        change: (UserData) -> UserData,
    ): MqResult<Unit> {
        calls += name
        gate?.await()
        failure?.let { return MqResult.Failure(it) }
        serverState[itemId] = change(serverState[itemId] ?: UserData())
        return MqResult.Success(Unit)
    }
}
