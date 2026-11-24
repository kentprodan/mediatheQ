package io.github.kentprodan.mediatheq.core.playback

import android.media.MediaCodecList
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
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
    }
