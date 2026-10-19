package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.model.DeviceProfile
import io.github.kentprodan.mediatheq.core.model.TranscodingTarget

/**
 * Builds the profile sent with every stream request (media3 skill, section 3), from real device facts:
 * - unmetered: no bitrate cap, every decodable format plays as it is (original quality);
 * - metered: capped at 256 kbps, so larger files (lossless, high bitrate) are converted to Opus;
 * - formats the device cannot decode are left out, so the server converts them.
 */
object DeviceProfileBuilder {
    /** Opus in Ogg on metered networks (ADR 0016); stereo is enough for music on a phone. */
    val OPUS_256 = TranscodingTarget(container = "ogg", codec = "opus", bitrate = 256_000, maxChannels = 2)

    /** Containers Media3's extractors read (Jellyfin names). */
    val CONTAINERS = listOf("flac", "mp3", "m4a", "mp4", "m4b", "aac", "ogg", "oga", "opus", "webm", "mka", "wav")

    private const val METERED_BITRATE = 256_000

    fun build(
        decoderMimeTypes: Set<String>,
        metered: Boolean,
    ): DeviceProfile =
        DeviceProfile(
            directPlayContainers = CONTAINERS,
            directPlayCodecs = CODECS.filter { (_, mime) -> mime == null || mime in decoderMimeTypes }.map { it.first },
            transcoding = OPUS_256,
            maxStreamingBitrate = if (metered) METERED_BITRATE else null,
        )

    /**
     * Jellyfin codec name -> decoder MIME type needed; `null` = Media3 handles it without a platform decoder (PCM).
     * shortcut: E-AC-3 counts only with a JOC (Atmos) decoder - a plain E-AC-3 decoder (Pixel) fails on Atmos music
     * since Media3 1.11 removed the fallback. Atmos detection and the stereo fallback come in #24.
     */
    private val CODECS =
        listOf(
            "flac" to "audio/flac",
            "mp3" to "audio/mpeg",
            "aac" to "audio/mp4a-latm",
            "alac" to "audio/alac",
            "opus" to "audio/opus",
            "vorbis" to "audio/vorbis",
            "ac3" to "audio/ac3",
            "eac3" to "audio/eac3-joc",
            "pcm_s16le" to null,
            "pcm_s24le" to null,
        )
}
