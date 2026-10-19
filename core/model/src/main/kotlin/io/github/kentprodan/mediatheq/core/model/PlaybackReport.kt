package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration

/** One playback state report for the server: where in [stream] the player is. */
data class PlaybackReport(
    val stream: PlaybackStream,
    val position: Duration,
    val isPaused: Boolean,
)
