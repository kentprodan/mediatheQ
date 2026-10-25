package io.github.kentprodan.mediatheq.core.playback

import kotlin.math.pow

/** How the player outputs audio (media3 skill, section 7). #22 adds bit-perfect USB, #24 Atmos passthrough. */
internal enum class AudioMode {
    /** Decoded on the audio DSP to save battery; volume (normalization) still applies. */
    Offload,

    /** Decoded to PCM in the app, for processing that needs the samples (EQ, #22). */
    Processed,
}

/** Which ReplayGain-style gain sets the volume of a track. */
internal enum class Normalization { Album, Track, Off }

/** The user's audio settings; defaults until the Settings screen exists (#25). */
internal data class AudioSettings(
    val normalization: Normalization = Normalization.Album,
    val equalizerOn: Boolean = false,
)

/** The one place that decides offload and normalization (media3 skill, sections 5 and 7). Pure, no Android. */
internal object AudioPolicy {
    fun decide(settings: AudioSettings): AudioMode =
        if (settings.equalizerOn) AudioMode.Processed else AudioMode.Offload

    /**
     * Player volume for a track with these gains: album gain with the track's as fallback (or the other way round in
     * [Normalization.Track]). Never above 1: a positive gain would need a limiter to avoid clipping.
     */
    fun volume(
        gains: ItemGains,
        normalization: Normalization,
    ): Float {
        val db =
            when (normalization) {
                Normalization.Album -> gains.albumDb ?: gains.trackDb
                Normalization.Track -> gains.trackDb ?: gains.albumDb
                Normalization.Off -> null
            } ?: return 1f
        return 10.0.pow(db / 20).toFloat().coerceIn(0f, 1f)
    }
}
