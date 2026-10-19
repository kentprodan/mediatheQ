package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration
import kotlin.time.Instant

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
    val sortName: String = title,
    val dateAdded: Instant? = null,
    /** Track ReplayGain-style gain in dB. */
    val normalizationGainDb: Double? = null,
    val artwork: Artwork? = null,
    val userData: UserData = UserData(),
)
