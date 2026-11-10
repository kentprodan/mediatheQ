package io.github.kentprodan.mediatheq.core.data

/** Settings -> Playback -> Transitions (#142): fades in milliseconds, 0 = disabled. Crossfade comes with #219. */
data class TransitionsPreferences(
    /** On play, resume and seek. */
    val fadeInMs: Int = 0,
    /** On pause and stop. */
    val fadeOutMs: Int = 0,
) {
    companion object {
        const val MAX_FADE_MS = 1_000
        const val FADE_STEP_MS = 100

        /** [ms] as a fade length: 0 to [MAX_FADE_MS] in steps of [FADE_STEP_MS]. */
        fun fadeMs(ms: Int): Int = ms.coerceIn(0, MAX_FADE_MS) / FADE_STEP_MS * FADE_STEP_MS
    }
}
