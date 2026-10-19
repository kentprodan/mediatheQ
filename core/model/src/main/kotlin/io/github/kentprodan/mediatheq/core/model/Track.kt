package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration

/** A playable music track. */
data class Track(
    val id: MediaId,
    val title: String,
    val artists: List<Artist>,
    val duration: Duration,
    val albumId: MediaId? = null,
    val albumTitle: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val audioFormat: AudioFormat? = null,
)
