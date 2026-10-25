package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.model.DeviceProfile
import io.github.kentprodan.mediatheq.core.model.TranscodingTarget

/**
 * Builds the profile sent with every stream request (media3 skill, section 3), from real device facts:
 * - [StreamingQuality.Original]: no bitrate cap, every decodable format plays as it is;
 * - 256 or 128 kbps: capped, so larger files (lossless, high bitrate) are converted to Opus at that bitrate;
 * - formats the device cannot decode are left out, so the server converts them (Opus 256 when there is no cap).
 */
object DeviceProfileBuilder {
    /** Opus in Ogg (ADR 0016) at [bitrate]; stereo is enough for music on a phone. */
    fun opus(bitrate: Int) = TranscodingTarget(container = "ogg", codec = "opus", bitrate = bitrate, maxChannels = 2)

    val OPUS_256 = opus(256_000)

    /** Containers Media3's extractors read (Jellyfin names). */
    val CONTAINERS = listOf("flac", "mp3", "m4a", "mp4", "m4b", "aac", "ogg", "oga", "opus", "webm", "mka", "wav")

    fun build(
        decoderMimeTypes: Set<String>,
        quality: StreamingQuality,
    ): DeviceProfile =
        DeviceProfile(
            directPlayContainers = CONTAINERS,
            directPlayCodecs = CODECS.filter { (_, mime) -> mime == null || mime in decoderMimeTypes }.map { it.first },
            transcoding = quality.bitrate?.let(::opus) ?: OPUS_256,
            maxStreamingBitrate = quality.bitrate,
        )

    /**
     * Jellyfin codec name -> decoder MIME type needed; `null` = Media3 handles it without a platform decoder (PCM).
     * E-AC-3 counts only with a JOC (Atmos) decoder: a plain E-AC-3 decoder (Pixel) fails on Atmos music since Media3
     * 1.11 removed the fallback, so the server converts it to stereo Opus (#24). TrueHD is never declared: phones have
     * no decoder for it. shortcut: no passthrough routes (HDMI, eARC) yet; they come with the TV phase (F2).
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
