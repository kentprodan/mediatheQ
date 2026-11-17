package io.github.kentprodan.mediatheq.core.data

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Where Resume starts (owner, #429, #439): 10 s before the saved point, so the scene is remembered. */
fun resumeStart(saved: Duration): Duration = (saved - RESUME_REWIND).coerceAtLeast(Duration.ZERO)

private val RESUME_REWIND = 10.seconds
