package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration
import kotlin.time.Instant

/** A music album. */
data class Album(
    val id: MediaId,
    val title: String,
    val albumArtists: List<Artist>,
    val year: Int? = null,
    val trackCount: Int = 0,
    val duration: Duration = Duration.ZERO,
    val sortName: String = title,
    val dateAdded: Instant? = null,
    /** Album ReplayGain-style gain in dB, for album normalization. */
    val normalizationGainDb: Double? = null,
    val artwork: Artwork? = null,
    val userData: UserData = UserData(),
)
