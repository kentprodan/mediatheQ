package io.github.kentprodan.mediatheq.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.VideoRange
import io.github.kentprodan.mediatheq.core.playback.VideoPlayback

/** The notes under the player's title (#276): the info line when asked for, a conversion, Dolby Vision missing. */
@Composable
internal fun videoNotes(
    info: VideoPlayback?,
    showInfo: Boolean,
    dolbyVisionUnavailable: Boolean,
): List<String> =
    listOfNotNull(
        info?.takeIf { showInfo }?.let { infoLine(it, playMethodLabel(it.playMethod)) },
        stringResource(R.string.video_converted).takeIf { info?.playMethod == PlayMethod.Transcode },
        info?.takeIf { dolbyVisionUnavailable }?.let {
            stringResource(R.string.video_dolby_vision_unavailable, rangeLabel(it.range))
        },
    )

/** "HEVC 3840 × 2160 · HDR10 · Direct play · c2.exynos.hevc.decoder"; unknown parts are left out. Pure. */
internal fun infoLine(
    info: VideoPlayback,
    method: String?,
): String =
    listOfNotNull(
        listOfNotNull(codecLabel(info.mimeType), "${info.width} × ${info.height}".takeIf { info.width > 0 })
            .joinToString(" ")
            .ifEmpty { null },
        rangeLabel(info.range),
        method,
        info.decoder,
    ).joinToString(" · ")

private fun codecLabel(mime: String?): String? =
    when (mime) {
        "video/hevc" -> "HEVC"
        "video/avc" -> "H.264"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/dolby-vision" -> "Dolby Vision"
        else -> mime?.substringAfter('/')?.uppercase()
    }

private fun rangeLabel(range: VideoRange): String =
    when (range) {
        VideoRange.Sdr -> "SDR"
        VideoRange.Hdr10 -> "HDR10"
        VideoRange.Hdr10Plus -> "HDR10+"
        VideoRange.Hlg -> "HLG"
        VideoRange.DolbyVision -> "Dolby Vision"
    }

@Composable
private fun playMethodLabel(method: PlayMethod?): String? =
    when (method) {
        PlayMethod.DirectPlay -> stringResource(R.string.video_method_direct_play)
        PlayMethod.DirectStream -> stringResource(R.string.video_method_direct_stream)
        PlayMethod.Transcode -> stringResource(R.string.video_method_transcode)
        null -> null
    }
