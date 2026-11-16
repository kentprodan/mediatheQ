package io.github.kentprodan.mediatheq.feature.tv.video

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Where a D-pad seek in a video lands (#429): 10 s back or 30 s forward a press. Held, the step grows every
 * [REPEATS_PER_STEP] repeats (remotes repeat at different rates), up to a ceiling that grows with the runtime: x2 under
 * 30 min, x4 under 90, x6 under 150, x10 beyond (Wholphin's idea). Kept inside the video.
 */
fun videoSeekTarget(
    position: Duration,
    duration: Duration,
    forward: Boolean,
    repeat: Int,
): Duration {
    val ceiling =
        when {
            duration < 30.minutes -> 2
            duration < 90.minutes -> 4
            duration < 150.minutes -> 6
            else -> 10
        }
    val factor = (1 + repeat / REPEATS_PER_STEP).coerceAtMost(ceiling)
    val step = (if (forward) FORWARD else BACK) * factor
    val target = if (forward) position + step else position - step
    return target.coerceIn(Duration.ZERO, duration.takeIf { it > Duration.ZERO } ?: target)
}

/** Where Resume starts (owner, #429): 10 s before the saved point, so the scene is remembered. */
fun resumeStart(saved: Duration): Duration = (saved - RESUME_REWIND).coerceAtLeast(Duration.ZERO)

private val FORWARD = 30.seconds
private val BACK = 10.seconds
private val RESUME_REWIND = 10.seconds
private const val REPEATS_PER_STEP = 3
