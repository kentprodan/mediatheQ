package io.github.kentprodan.mediatheq.core.model

import kotlin.math.roundToInt
import kotlin.time.Instant

/** What the server remembers about an item for the signed-in user. */
data class UserData(
    val isFavorite: Boolean = false,
    /** The server's rating, 0-10, or `null` when unrated. */
    val rating: Double? = null,
    val playCount: Int = 0,
    val lastPlayedAt: Instant? = null,
    val played: Boolean = false,
) {
    /** [rating] as 1-5 stars (the server keeps 0-10, one star = 2), `null` when unrated. */
    val stars: Int? get() = rating?.let { (it / 2).roundToInt().coerceIn(1, 5) }

    companion object {
        /** The server rating for [stars] 1-5. */
        fun ratingOf(stars: Int): Double {
            require(stars in 1..5) { "stars must be 1-5" }
            return stars * 2.0
        }
    }
}
