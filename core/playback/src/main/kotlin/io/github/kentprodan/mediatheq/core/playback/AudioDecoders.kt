package io.github.kentprodan.mediatheq.core.playback

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import io.github.kentprodan.mediatheq.core.model.VideoCodecLimit
import javax.inject.Inject

/** The audio formats this device can decode, as MIME types (`audio/flac`, ...). A snapshot, so logic stays pure. */
fun interface AudioDecoders {
    fun mimeTypes(): Set<String>
}

/** [AudioDecoders] from the platform's codec list and the FFmpeg decoder the player falls back to (#625). */
internal class MediaCodecAudioDecoders
    @Inject
    constructor() : AudioDecoders {
        override fun mimeTypes(): Set<String> =
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .filterNot { it.isEncoder }
                .flatMap { it.supportedTypes.asList() }
                .filter { it.startsWith("audio/") }
                .toSet() + ffmpegMimeTypes()
    }

/** What the bundled FFmpeg decoder reads, of the formats a profile can name; none when its library failed to load. */
@OptIn(UnstableApi::class)
private fun ffmpegMimeTypes(): Set<String> =
    if (FfmpegLibrary.isAvailable()) FFMPEG_CANDIDATES.filter(FfmpegLibrary::supportsFormat).toSet() else emptySet()

private val FFMPEG_CANDIDATES =
    listOf(
        MimeTypes.AUDIO_AC3,
        MimeTypes.AUDIO_E_AC3,
        MimeTypes.AUDIO_E_AC3_JOC,
        MimeTypes.AUDIO_TRUEHD,
        MimeTypes.AUDIO_DTS,
        MimeTypes.AUDIO_DTS_HD,
        MimeTypes.AUDIO_FLAC,
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_MPEG,
        MimeTypes.AUDIO_AAC,
        MimeTypes.AUDIO_OPUS,
        MimeTypes.AUDIO_VORBIS,
    )

/** The video formats this device can decode, as MIME types (`video/avc`, ...). A snapshot, like [AudioDecoders]. */
fun interface VideoDecoders {
    fun mimeTypes(): Set<String>

    /** What each decoder takes (#636); none known by default. */
    fun limits(): List<VideoCodecLimit> = emptyList()
}

/** [VideoDecoders] from the platform's codec list. */
internal class MediaCodecVideoDecoders
    @Inject
    constructor() : VideoDecoders {
        override fun mimeTypes(): Set<String> =
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .filterNot { it.isEncoder }
                .flatMap { it.supportedTypes.asList() }
                .filter { it.startsWith("video/") }
                .toSet()

        override fun limits(): List<VideoCodecLimit> {
            val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
            return LIMITED_CODECS.mapNotNull { (codec, mime) ->
                val decoders = infos.filter { mime in it.supportedTypes }
                // A hardware decoder when there is one: a software one would take more than plays smoothly.
                val chosen = decoders.filter { it.isHardwareAccelerated }.ifEmpty { decoders }
                if (chosen.isEmpty()) return@mapNotNull null
                val caps = chosen.map { it.getCapabilitiesForType(mime) }
                val levels = caps.flatMap { it.profileLevels.asList() }
                VideoCodecLimit(
                    codec = codec,
                    maxWidth = caps.mapNotNull { it.videoCapabilities?.supportedWidths?.upper }.maxOrNull(),
                    maxHeight = caps.mapNotNull { it.videoCapabilities?.supportedHeights?.upper }.maxOrNull(),
                    maxLevel = levels.mapNotNull { levelIdc(mime, it.level) }.maxOrNull(),
                    profiles = profileNames(mime, levels.map { it.profile }.toSet()),
                )
            }
        }
    }

private val LIMITED_CODECS =
    listOf(
        "h264" to "video/avc",
        "hevc" to "video/hevc",
        "av1" to "video/av01",
        "vp9" to "video/x-vnd.on2.vp9",
    )

/** A decoder level constant ([CodecProfileLevel]) as the server's `level_idc`; `null` for codecs it is not kept for. */
internal fun levelIdc(
    mime: String,
    level: Int,
): Int? =
    when (mime) {
        "video/avc" -> AVC_LEVELS[level]

        // Main and high tier share the level: bit 2i is main tier, 2i + 1 high tier.
        "video/hevc" -> HEVC_LEVELS.getOrNull(Integer.numberOfTrailingZeros(level) / 2)?.takeIf { level != 0 }

        else -> null
    }

/** The server's profile names a decoder with these [profiles] takes (H.264 and HEVC); `null` for other codecs. */
internal fun profileNames(
    mime: String,
    profiles: Set<Int>,
): List<String>? =
    when (mime) {
        "video/avc" -> {
            buildList {
                addAll(listOf("baseline", "constrained baseline", "main", "high"))
                if (CodecProfileLevel.AVCProfileHigh10 in profiles) add("high 10")
            }
        }

        "video/hevc" -> {
            buildList {
                add("main")
                val main10 =
                    listOf(
                        CodecProfileLevel.HEVCProfileMain10,
                        CodecProfileLevel.HEVCProfileMain10HDR10,
                        CodecProfileLevel.HEVCProfileMain10HDR10Plus,
                    )
                if (profiles.any { it in main10 }) add("main 10")
            }
        }

        else -> {
            null
        }
    }

private val AVC_LEVELS =
    mapOf(
        CodecProfileLevel.AVCLevel1 to 10,
        CodecProfileLevel.AVCLevel1b to 9,
        CodecProfileLevel.AVCLevel11 to 11,
        CodecProfileLevel.AVCLevel12 to 12,
        CodecProfileLevel.AVCLevel13 to 13,
        CodecProfileLevel.AVCLevel2 to 20,
        CodecProfileLevel.AVCLevel21 to 21,
        CodecProfileLevel.AVCLevel22 to 22,
        CodecProfileLevel.AVCLevel3 to 30,
        CodecProfileLevel.AVCLevel31 to 31,
        CodecProfileLevel.AVCLevel32 to 32,
        CodecProfileLevel.AVCLevel4 to 40,
        CodecProfileLevel.AVCLevel41 to 41,
        CodecProfileLevel.AVCLevel42 to 42,
        CodecProfileLevel.AVCLevel5 to 50,
        CodecProfileLevel.AVCLevel51 to 51,
        CodecProfileLevel.AVCLevel52 to 52,
        CodecProfileLevel.AVCLevel6 to 60,
        CodecProfileLevel.AVCLevel61 to 61,
        CodecProfileLevel.AVCLevel62 to 62,
    )

// HEVC levels 1 to 6.2 in CodecProfileLevel order, as level_idc (30 x the level).
private val HEVC_LEVELS = listOf(30, 60, 63, 90, 93, 120, 123, 150, 153, 156, 180, 183, 186)
