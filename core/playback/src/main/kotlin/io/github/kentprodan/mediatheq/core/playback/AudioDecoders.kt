package io.github.kentprodan.mediatheq.core.playback

import android.media.MediaCodecList
import javax.inject.Inject

/** The audio formats this device can decode, as MIME types (`audio/flac`, ...). A snapshot, so logic stays pure. */
fun interface AudioDecoders {
    fun mimeTypes(): Set<String>
}

/** [AudioDecoders] from the platform's codec list. */
internal class MediaCodecAudioDecoders
    @Inject
    constructor() : AudioDecoders {
        override fun mimeTypes(): Set<String> =
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .filterNot { it.isEncoder }
                .flatMap { it.supportedTypes.asList() }
                .filter { it.startsWith("audio/") }
                .toSet()
    }
