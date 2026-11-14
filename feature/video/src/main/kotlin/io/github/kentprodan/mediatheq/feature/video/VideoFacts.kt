package io.github.kentprodan.mediatheq.feature.video

import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoRange

/**
 * The format line of a video page (#273): "4K · HEVC · HDR10 · 5.1" from its one video stream and its default (else
 * first) audio stream; `null` when nothing is known.
 */
internal fun formatLine(
    video: Video,
    streams: List<MediaStreamInfo>,
): String? {
    val format = video.format
    val resolution =
        format?.let {
            val width = it.width ?: 0
            val height = it.height ?: 0
            when {
                width >= 3800 || height >= 2100 -> "4K"
                width >= 1900 || height >= 1000 -> "1080p"
                width >= 1200 || height >= 700 -> "720p"
                height > 0 -> "SD"
                else -> null
            }
        }
    val codec =
        format?.codec?.lowercase()?.let {
            when (it) {
                "hevc", "h265" -> "HEVC"
                "h264", "avc" -> "H.264"
                "av1" -> "AV1"
                "vp9" -> "VP9"
                else -> it.uppercase()
            }
        }
    val range =
        when (format?.range) {
            VideoRange.Hdr10 -> "HDR10"
            VideoRange.Hdr10Plus -> "HDR10+"
            VideoRange.Hlg -> "HLG"
            VideoRange.DolbyVision -> "Dolby Vision"
            VideoRange.Sdr, null -> null
        }
    val audio = streams.filter { it.type == StreamType.Audio }
    val channels =
        (audio.firstOrNull { it.isDefault } ?: audio.firstOrNull())?.channels?.let {
            when (it) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> "$it ch"
            }
        }
    return listOfNotNull(resolution, codec, range, channels).joinToString(" · ").ifEmpty { null }
}

/** How many subtitle files sit beside the video (shown as "2 subtitle files"). */
internal fun externalSubtitles(streams: List<MediaStreamInfo>): Int =
    streams.count { it.type == StreamType.Subtitle && it.isExternal }
