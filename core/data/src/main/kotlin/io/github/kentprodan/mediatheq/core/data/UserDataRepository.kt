package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId

/**
 * Favorite and rating of tracks, albums and artists. A change shows in Room at once, then goes to the server; a failed
 * change is undone and returned as a failure. Changes made while the track plays survive playback reporting (R2).
 */
interface UserDataRepository {
    suspend fun setFavorite(
        id: MediaId,
        isFavorite: Boolean,
    ): MqResult<Unit>

    /** Rates [id] with 1-5 [stars]; `null` clears the rating. */
    suspend fun setRating(
        id: MediaId,
        stars: Int?,
    ): MqResult<Unit>
}
