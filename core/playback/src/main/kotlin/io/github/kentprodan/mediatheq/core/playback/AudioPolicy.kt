package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MimeTypes
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import kotlin.math.pow

/** How the player outputs audio (media3 skill, section 7). */
internal enum class AudioMode {
    /** Decoded on the audio DSP to save battery; volume (normalization) still applies. */
    Offload,

    /** Decoded to PCM in the app, for processing that needs the samples (EQ). */
    Processed,

    /** PCM sent unchanged to a USB DAC: no EQ, no normalization, no phone volume. */
    BitPerfect,

    /**
     * The encoded stream (E-AC-3 JOC, TrueHD) sent unchanged over HDMI for the receiver to decode (ADR 0039): no
     * offload, no EQ, no normalization, volume 1, no fades.
     */
    Passthrough,
}

/** The current audio output, as far as the policy cares. */
internal enum class AudioRoute { UsbDac, Hdmi, Other }

/** The user's audio settings, as the policy needs them. */
internal data class AudioSettings(
    val normalization: Normalization = Normalization.Album,
    val equalizerOn: Boolean = false,
    val bitPerfectUsb: Boolean = false,
    /** The output's Bypass processing (#140): no equalizer, no normalization; bit-perfect is unaffected. */
    val bypass: Boolean = false,
    /** The output's Dolby Atmos passthrough (ADR 0039): only HDMI outputs use it. */
    val atmosPassthrough: Boolean = true,
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
        /** What the route passes through ([PassthroughCapabilities]); empty off HDMI. */
        passthrough: Set<String> = emptySet(),
        /** The playing file's passthrough MIME type ([passthroughMime]); `null` when it is not one or not the file. */
        trackMime: String? = null,
    ): AudioMode =
        when {
            wantsBitPerfect(settings, route) && bitPerfectFormat -> {
                AudioMode.BitPerfect
            }

            wantsPassthrough(settings, route, passthrough) && passesThrough(trackMime, passthrough) -> {
                AudioMode.Passthrough
            }

            equalizer(settings) -> {
                AudioMode.Processed
            }

            else -> {
                AudioMode.Offload
            }
        }

    /**
     * Whether the player may offload. Decided per route, not per track: changing it re-selects tracks and drops the
     * gapless transition, and an offloaded track never reaches the bit-perfect mixer.
     */
    fun offload(
        settings: AudioSettings,
        route: AudioRoute,
        passthrough: Set<String> = emptySet(),
        /** A video plays (ADR 0047): offloaded audio cannot be kept in sync with the picture. */
        video: Boolean = false,
    ): Boolean =
        !video &&
            !equalizer(settings) &&
            !wantsBitPerfect(settings, route) &&
            !wantsPassthrough(settings, route, passthrough)

    /** Whether the equalizer is wanted: on, and the output does not bypass processing. */
    private fun equalizer(settings: AudioSettings) = settings.equalizerOn && !settings.bypass

    /** The normalization that applies: none when the output bypasses processing (#140). */
    fun normalization(settings: AudioSettings): Normalization =
        if (settings.bypass) Normalization.Off else settings.normalization

    /** Whether to look for a bit-perfect mixer attribute at all. */
    fun wantsBitPerfect(
        settings: AudioSettings,
        route: AudioRoute,
    ): Boolean = settings.bitPerfectUsb && route == AudioRoute.UsbDac

    /**
     * Whether this route may pass encoded audio through (ADR 0039): HDMI, the output's switch on, and something it
     * can pass. Per route, like [offload]; which track actually does is [decide]'s.
     */
    fun wantsPassthrough(
        settings: AudioSettings,
        route: AudioRoute,
        passthrough: Set<String>,
    ): Boolean = settings.atmosPassthrough && route == AudioRoute.Hdmi && passthrough.isNotEmpty()

    /**
     * Whether the sink passes [trackMime] through on a route that takes [passthrough]: JOC also goes out as plain E-AC-3
     * where only that is supported (Media3 falls back to it; the receiver then plays the 5.1 core).
     */
    private fun passesThrough(
        trackMime: String?,
        passthrough: Set<String>,
    ): Boolean =
        trackMime in passthrough || (trackMime == MimeTypes.AUDIO_E_AC3_JOC && MimeTypes.AUDIO_E_AC3 in passthrough)

    /** Modes where the player volume stays 1: bit-perfect samples and passthrough bitstreams reach the device as is. */
    fun fixedVolume(mode: AudioMode): Boolean = mode == AudioMode.BitPerfect || mode == AudioMode.Passthrough

    /**
     * Whether a Dolby Atmos track plays as the server's stereo conversion (no JOC decoder or passthrough, or the
     * metered bitrate cap; media3 skill, section 4), or plays from a [convertedDownload] (reported as a direct play,
     * #89), so Now Playing says Atmos is not available. `false` while the stream is not resolved yet or playback stopped
     * ([playMethod] `null`): unknown is not a downgrade.
     */
    fun atmosConverted(
        isAtmos: Boolean,
        playMethod: PlayMethod?,
        convertedDownload: Boolean,
    ): Boolean = isAtmos && playMethod != null && !playsOriginal(playMethod, convertedDownload)

    /**
     * Whether a Dolby Atmos file plays as it is but is decoded on this device (#242, ADR 0039): the original reached
     * the player ([playsOriginal]), the AudioTrack it opened carries PCM ([decoderUsed]) and no Atmos bitstream left the
     * device ([passthroughConfirmed]). Now Playing then says "Decoded on this output". `false` while nothing opened yet.
     */
    fun atmosDecoded(
        isAtmos: Boolean,
        playMethod: PlayMethod?,
        convertedDownload: Boolean,
        passthroughConfirmed: Boolean,
        decoderUsed: Boolean,
    ): Boolean = isAtmos && playsOriginal(playMethod, convertedDownload) && decoderUsed && !passthroughConfirmed

    /**
     * Whether the AudioTrack the sink opened last ([openedEncoding]) decodes to PCM, for [atmosDecoded]. Not while
     * [mode] expects a passthrough: right after a transition the encoding is still the previous track's (#242 review).
     */
    fun decoderUsed(
        openedEncoding: Int?,
        mode: AudioMode,
    ): Boolean = openedEncoding != null && !isPassthroughEncoding(openedEncoding) && mode != AudioMode.Passthrough

    /**
     * The bitrate of the server's conversion that plays (#125), `null` when the original file plays: a transcode at
     * the stream's quality cap ([qualityKbps], none for Original: then only an undecodable format converts, to Opus
     * 256), or a converted download at the bitrate it was made with ([convertedDownloadKbps], #144).
     * shortcut: a DirectStream (remux) counts as the original codec; Jellyfin reports remuxes as transcodes, so one
     * shows as converted. Read the codec and bitrate from the transcoding URL if that matters.
     */
    fun convertedKbps(
        playMethod: PlayMethod,
        convertedDownloadKbps: Int?,
        qualityKbps: Int?,
    ): Int? =
        when {
            convertedDownloadKbps != null -> convertedDownloadKbps
            playMethod == PlayMethod.Transcode -> qualityKbps ?: CONVERTED_DOWNLOAD_KBPS
            else -> null
        }

    /**
     * Whether the player decodes the original file: a direct play, not a [convertedDownload] (which also reports as a
     * direct play, #113). Only then does the file's format in the media item extras hold, e.g. for bit-perfect USB.
     */
    fun playsOriginal(
        playMethod: PlayMethod?,
        convertedDownload: Boolean,
    ): Boolean = playMethod == PlayMethod.DirectPlay && !convertedDownload

    /**
     * Player volume for a track with these gains in [mode]: album gain with the track's as fallback (or the other way
     * round in [Normalization.Track]). Never above 1: a positive gain would need a limiter to avoid clipping. Always 1
     * in bit-perfect and passthrough modes ([fixedVolume]), where the audio must reach the device unchanged.
     */
    fun volume(
        gains: ItemGains,
        normalization: Normalization,
        mode: AudioMode,
    ): Float {
        if (fixedVolume(mode)) return 1f
        val db =
            when (normalization) {
                Normalization.Album -> gains.albumDb ?: gains.trackDb
                Normalization.Track -> gains.trackDb ?: gains.albumDb
                Normalization.Off -> null
            } ?: return 1f
        return 10.0.pow(db / 20).toFloat().coerceIn(0f, 1f)
    }

    /** Converted downloads and conversions of undecodable formats are Opus at this bitrate (ADR 0024). */
    private const val CONVERTED_DOWNLOAD_KBPS = 256
}

/**
 * The MIME type a file passes through as (ADR 0039), from its codec as the server names it: E-AC-3 with Dolby Atmos is
 * JOC. `null` for anything that cannot pass through (PCM, FLAC, AAC, ...).
 */
internal fun passthroughMime(
    codec: String?,
    isAtmos: Boolean,
): String? =
    when (codec?.lowercase()) {
        "eac3" -> if (isAtmos) MimeTypes.AUDIO_E_AC3_JOC else MimeTypes.AUDIO_E_AC3
        "ac3" -> MimeTypes.AUDIO_AC3
        "truehd" -> MimeTypes.AUDIO_TRUEHD
        else -> null
    }
