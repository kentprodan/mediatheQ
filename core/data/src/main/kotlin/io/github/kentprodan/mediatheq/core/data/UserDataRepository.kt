package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.flow.StateFlow

/**
 * Favorite and rating of tracks, albums and artists. A change shows in Room at once, then goes to the server; a failed
 * change is undone and returned as a failure. Changes made while the track plays survive playback reporting (R2).
 */
interface UserDataRepository {
    /** The last change the server refused (a 401 signs out instead), for one app-wide message; see [dismissError]. */
    val error: StateFlow<MqError?>

    fun dismissError()

    suspend fun setFavorite(
        id: MediaId,
        isFavorite: Boolean,
    ): MqResult<Unit>

    /** Rates [id] with [halfStars], 1-10 (whole stars are even); `null` clears the rating. */
    suspend fun setRating(
        id: MediaId,
        halfStars: Int?,
    ): MqResult<Unit>
}
