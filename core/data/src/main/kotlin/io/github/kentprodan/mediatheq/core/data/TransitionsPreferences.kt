package io.github.kentprodan.mediatheq.core.data

/** Settings -> Playback -> Transitions (#142, #219): lengths in milliseconds, 0 = disabled. */
data class TransitionsPreferences(
    /** On play, resume and seek. */
    val fadeInMs: Int = 0,
    /** On pause and stop. */
    val fadeOutMs: Int = 0,
    /** Between tracks that do not follow each other on an album (#219). */
    val crossfadeMs: Int = 0,
    /** Starts the crossfade where the outgoing track gets quiet, from its waveform (#219). */
    val smartFades: Boolean = false,
    val fadeOutCurve: FadeCurve = FadeCurve.EqualPower,
    val fadeInCurve: FadeCurve = FadeCurve.EqualPower,
) {
    companion object {
        const val MAX_FADE_MS = 1_000
        const val FADE_STEP_MS = 100
        const val MAX_CROSSFADE_MS = 12_000
        const val CROSSFADE_STEP_MS = 1_000

        /** [ms] as a fade length: 0 to [MAX_FADE_MS] in steps of [FADE_STEP_MS]. */
        fun fadeMs(ms: Int): Int = ms.coerceIn(0, MAX_FADE_MS) / FADE_STEP_MS * FADE_STEP_MS

        /** [ms] as a crossfade length: 0 to [MAX_CROSSFADE_MS] in whole seconds. */
        fun crossfadeMs(ms: Int): Int =
            (ms.coerceIn(0, MAX_CROSSFADE_MS) + CROSSFADE_STEP_MS / 2) / CROSSFADE_STEP_MS * CROSSFADE_STEP_MS
    }
}

/** How a crossfade's volume moves over its length (#219). */
enum class FadeCurve {
    Linear,

    /** Constant loudness through the overlap: sine and cosine. */
    EqualPower,

    /** Slow at first, fast at the end (or the reverse when fading out). */
    Exponential,
}
