package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration

/** Where listening to an album (#132) or a playlist (#135) stopped: a track of it and the position in that track. */
data class ResumePoint(
    val trackId: MediaId,
    val position: Duration,
)
