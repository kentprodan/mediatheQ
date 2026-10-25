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
    /**
     * [rating] in half stars, 1-10: the server's 0-10 scale, one half star = 1. `null` when unrated; a rating set
     * elsewhere is rounded, and anything above 0 is at least half a star (0 itself means unrated, R14).
     */
    val halfStars: Int? get() = rating?.let { it.roundToInt().coerceIn(1, 10) }
}
