package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration

/** A music album. */
data class Album(
    val id: MediaId,
    val title: String,
    val albumArtist: String,
    val year: Int? = null,
    val trackCount: Int = 0,
    val duration: Duration = Duration.ZERO,
)
