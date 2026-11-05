package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration

/** Where listening to an album stopped (#132): a track of it and the position in that track. */
data class AlbumResume(
    val trackId: MediaId,
    val position: Duration,
)
