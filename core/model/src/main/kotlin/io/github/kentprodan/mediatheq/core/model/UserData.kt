package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Instant

/** What the server remembers about an item for the signed-in user. */
data class UserData(
    val isFavorite: Boolean = false,
    /** The server's rating, 0-10, or `null` when unrated. */
    val rating: Double? = null,
    val playCount: Int = 0,
    val lastPlayedAt: Instant? = null,
    val played: Boolean = false,
)
