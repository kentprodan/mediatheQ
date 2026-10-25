package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.model.PlayMethod
import kotlin.math.pow

/** How the player outputs audio (media3 skill, section 7). Atmos passthrough comes with the TV phase (F2). */
internal enum class AudioMode {
    /** Decoded on the audio DSP to save battery; volume (normalization) still applies. */
    Offload,

    /** Decoded to PCM in the app, for processing that needs the samples (EQ). */
    Processed,

    /** PCM sent unchanged to a USB DAC: no EQ, no normalization, no phone volume. */
    BitPerfect,
}

/** Which ReplayGain-style gain sets the volume of a track. */
internal enum class Normalization { Album, Track, Off }

/** The current audio output, as far as the policy cares. HDMI comes with the TV phase (F2). */
internal enum class AudioRoute { UsbDac, Other }

/** The user's audio settings. Normalization keeps its default until the Settings screen exists (#25). */
internal data class AudioSettings(
    val normalization: Normalization = Normalization.Album,
    val equalizerOn: Boolean = false,
    val bitPerfectUsb: Boolean = false,
)

/** The one place that decides offload, bit-perfect, EQ and normalization (media3 skill, sections 5-7). Pure. */
internal object AudioPolicy {
    /**
     * The mode for the playing track. Bit-perfect only when the DAC accepted a mixer attribute for this track's format
     * ([bitPerfectFormat]); otherwise the track plays normally, with EQ and normalization (media3 skill, section 6.4).
     */
    fun decide(
        settings: AudioSettings,
        route: AudioRoute,
        bitPerfectFormat: Boolean,
    ): AudioMode =
        when {
            wantsBitPerfect(settings, route) && bitPerfectFormat -> AudioMode.BitPerfect
            settings.equalizerOn -> AudioMode.Processed
            else -> AudioMode.Offload
        }

    /**
     * Whether the player may offload. Decided per route, not per track: changing it re-selects tracks and drops the
     * gapless transition, and an offloaded track never reaches the bit-perfect mixer.
     */
    fun offload(
        settings: AudioSettings,
        route: AudioRoute,
    ): Boolean = !settings.equalizerOn && !wantsBitPerfect(settings, route)

    /** Whether to look for a bit-perfect mixer attribute at all. */
    fun wantsBitPerfect(
        settings: AudioSettings,
        route: AudioRoute,
    ): Boolean = settings.bitPerfectUsb && route == AudioRoute.UsbDac

    /**
     * Whether a Dolby Atmos track plays as the server's stereo conversion (no JOC decoder or passthrough, or the
     * metered bitrate cap; media3 skill, section 4), so Now Playing says Atmos is not available. `false` while the
     * stream is not resolved yet or playback stopped ([playMethod] `null`): unknown is not a downgrade.
     */
    fun atmosConverted(
        isAtmos: Boolean,
        playMethod: PlayMethod?,
    ): Boolean = isAtmos && playMethod != null && playMethod != PlayMethod.DirectPlay

    /**
     * Player volume for a track with these gains in [mode]: album gain with the track's as fallback (or the other way
     * round in [Normalization.Track]). Never above 1: a positive gain would need a limiter to avoid clipping. Always 1
     * in bit-perfect mode, where the samples must reach the DAC unchanged.
     */
    fun volume(
        gains: ItemGains,
        normalization: Normalization,
        mode: AudioMode,
    ): Float {
        if (mode == AudioMode.BitPerfect) return 1f
        val db =
            when (normalization) {
                Normalization.Album -> gains.albumDb ?: gains.trackDb
                Normalization.Track -> gains.trackDb ?: gains.albumDb
                Normalization.Off -> null
            } ?: return 1f
        return 10.0.pow(db / 20).toFloat().coerceIn(0f, 1f)
    }
}
