package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.model.DeviceProfile
import io.github.kentprodan.mediatheq.core.model.TranscodingTarget
import io.github.kentprodan.mediatheq.core.model.VideoQuality

/**
 * Builds the profile sent with every stream request (media3 skill, section 3), from real device facts:
 * - [StreamingQuality.Original]: no bitrate cap, every decodable format plays as it is;
 * - 256 or 128 kbps: capped, so larger files (lossless, high bitrate) are converted to Opus at that bitrate;
 * - formats the device cannot decode are left out, so the server converts them (Opus 256 when there is no cap).
 */
object DeviceProfileBuilder {
    /** Opus at [bitrate] in HLS fMP4 segments, so converted tracks can be seeked (ADR 0019); stereo is enough on a phone. */
    fun opus(bitrate: Int) =
        TranscodingTarget(container = "mp4", codec = "opus", bitrate = bitrate, maxChannels = 2, segmented = true)

    val OPUS_256 = opus(256_000)

    /**
     * Opus 256 in one Ogg file, for downloads of tracks the device cannot play as they are (#68, ADR 0024): a download
     * is a file, and Media3 seeks in a complete Ogg file.
     */
    val OPUS_256_FILE = TranscodingTarget(container = "ogg", codec = "opus", bitrate = 256_000, maxChannels = 2)

    /** Containers Media3's extractors read (Jellyfin names). */
    val CONTAINERS = listOf("flac", "mp3", "m4a", "mp4", "m4b", "aac", "ogg", "oga", "opus", "webm", "mka", "wav")

    /**
     * [passthroughMimeTypes] is what the route passes through unchanged (ADR 0039, [PassthroughCapabilities]): E-AC-3
     * counts with a JOC decoder or JOC passthrough, TrueHD only with TrueHD passthrough. Empty = today's phone profile.
     */
    fun build(
        decoderMimeTypes: Set<String>,
        passthroughMimeTypes: Set<String>,
        quality: StreamingQuality,
    ): DeviceProfile =
        DeviceProfile(
            directPlayContainers = CONTAINERS,
            directPlayCodecs =
                CODECS
                    .filter { (codec, mime) ->
                        when {
                            mime == null -> true
                            codec == "truehd" -> mime in passthroughMimeTypes
                            codec == "eac3" -> mime in decoderMimeTypes || mime in passthroughMimeTypes
                            else -> mime in decoderMimeTypes
                        }
                    }.map { it.first },
            transcoding = quality.bitrate?.let(::opus) ?: OPUS_256,
            maxStreamingBitrate = quality.bitrate,
        )

    /**
     * For a download (#144): at [quality] Original the original whenever the device plays it as it is, else one Opus
     * 256 file; at a bitrate always one Opus file at that bitrate (no direct play at all), smaller than the original.
     */
    fun download(
        decoderMimeTypes: Set<String>,
        quality: StreamingQuality = StreamingQuality.Original,
    ): DeviceProfile {
        // Downloads never pass through: they are files, and the TV has none (ADR 0039).
        val bitrate =
            quality.bitrate ?: return build(decoderMimeTypes, emptySet(), quality).copy(transcoding = OPUS_256_FILE)
        return DeviceProfile(
            directPlayContainers = emptyList(),
            directPlayCodecs = emptyList(),
            transcoding = OPUS_256_FILE.copy(bitrate = bitrate),
            maxStreamingBitrate = bitrate,
        )
    }

    /** Video containers Media3 reads (Jellyfin names, ADR 0050). */
    val VIDEO_CONTAINERS = listOf("mp4", "m4v", "mkv", "webm", "ts")

    /** What the server converts a video to: HLS fMP4, HEVC first, stereo or 5.1 AAC / Opus (ADR 0050). */
    val VIDEO_TRANSCODING =
        TranscodingTarget(
            container = "mp4",
            codec = "aac,opus",
            bitrate = 192_000,
            maxChannels = 6,
            segmented = true,
            videoCodec = "hevc,h264",
        )

    /**
     * The profile for streaming a video (ADR 0050): the video codecs the device decodes, the music rule for the audio
     * inside (E-AC-3 only with a JOC decoder, never TrueHD on a phone), and [quality]'s cap and height. HDR ranges come
     * with #276, so everything plays as SDR or is tone-mapped by the server.
     */
    fun video(
        audioMimeTypes: Set<String>,
        videoMimeTypes: Set<String>,
        quality: VideoQuality,
    ): DeviceProfile =
        DeviceProfile(
            directPlayContainers = VIDEO_CONTAINERS,
            directPlayCodecs = build(audioMimeTypes, emptySet(), StreamingQuality.Original).directPlayCodecs,
            transcoding = VIDEO_TRANSCODING,
            maxStreamingBitrate = quality.bitrate,
            videoCodecs = VIDEO_CODECS.filter { (_, mime) -> mime in videoMimeTypes }.map { it.first },
            maxHeight = quality.maxHeight,
        )

    private val VIDEO_CODECS =
        listOf(
            "h264" to "video/avc",
            "hevc" to "video/hevc",
            "av1" to "video/av01",
            "vp9" to "video/x-vnd.on2.vp9",
        )

    /**
     * Jellyfin codec name -> decoder MIME type needed; `null` = Media3 handles it without a platform decoder (PCM).
     * E-AC-3 counts only with a JOC (Atmos) decoder: a plain E-AC-3 decoder (Pixel) fails on Atmos music since Media3
     * 1.11 removed the fallback, so the server converts it to stereo Opus (#24). TrueHD is declared only where the route
     * passes it through ([build]): phones have no decoder for it.
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
            "truehd" to "audio/true-hd",
            "pcm_s16le" to null,
            "pcm_s24le" to null,
        )
}
