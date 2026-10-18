package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration

/** A user playlist. */
data class Playlist(
    val id: MediaId,
    val name: String,
    val trackCount: Int = 0,
    val duration: Duration = Duration.ZERO,
)
