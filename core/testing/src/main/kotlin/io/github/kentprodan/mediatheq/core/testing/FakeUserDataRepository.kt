package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.model.MediaId

/**
 * [UserDataRepository] for ViewModel tests: records each change in [calls] (e.g. "favorite:t1:true", "stars:t1:4") and
 * answers with [failure] when it is set.
 */
class FakeUserDataRepository : UserDataRepository {
    val calls = mutableListOf<String>()
    var failure: MqError? = null

    override suspend fun setFavorite(
        id: MediaId,
        isFavorite: Boolean,
    ) = record("favorite:${id.itemId}:$isFavorite")

    override suspend fun setRating(
        id: MediaId,
        stars: Int?,
    ) = record("stars:${id.itemId}:$stars")

    private fun record(call: String): MqResult<Unit> {
        calls += call
        return failure?.let { MqResult.Failure(it) } ?: MqResult.Success(Unit)
    }
}
