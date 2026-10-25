package io.github.kentprodan.mediatheq.core.server

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId

/** Per-user writes on an item: favorite and rating (jellyfin skill, section 7). Each call changes only its own value. */
interface UserDataSink {
    suspend fun setFavorite(
        server: SignedInServer,
        itemId: MediaId,
        isFavorite: Boolean,
    ): MqResult<Unit>

    /** Sets the rating, 0-10 as in `UserData.rating`; `null` clears it. */
    suspend fun setRating(
        server: SignedInServer,
        itemId: MediaId,
        rating: Double?,
    ): MqResult<Unit>
}
