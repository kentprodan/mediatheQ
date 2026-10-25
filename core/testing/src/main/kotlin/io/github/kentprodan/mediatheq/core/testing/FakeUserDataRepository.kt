package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [UserDataRepository] for ViewModel tests: records each change in [calls] (e.g. "favorite:t1:true", "rating:t1:8") and
 * answers with [failure] when it is set.
 */
class FakeUserDataRepository : UserDataRepository {
    val calls = mutableListOf<String>()
    var failure: MqError? = null
    override val error = MutableStateFlow<MqError?>(null)

    override fun dismissError() {
        error.value = null
    }

    override suspend fun setFavorite(
        id: MediaId,
        isFavorite: Boolean,
    ) = record("favorite:${id.itemId}:$isFavorite")

    override suspend fun setRating(
        id: MediaId,
        halfStars: Int?,
    ) = record("rating:${id.itemId}:$halfStars")

    private fun record(call: String): MqResult<Unit> {
        calls += call
        return failure?.let { MqResult.Failure(it) } ?: MqResult.Success(Unit)
    }
}
