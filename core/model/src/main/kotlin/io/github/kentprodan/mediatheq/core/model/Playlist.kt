package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration
import kotlin.time.Instant

/** A user playlist. */
data class Playlist(
    val id: MediaId,
    val name: String,
    val trackCount: Int = 0,
    val duration: Duration = Duration.ZERO,
    val sortName: String = name,
    val dateAdded: Instant? = null,
    val artwork: Artwork? = null,
    val userData: UserData = UserData(),
)
