package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.Format
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import io.github.kentprodan.mediatheq.core.common.AppLog

/**
 * What a video really plays as, into the app's log under `MqVideo` (#272): the stream's container and codec, the
 * decoder the device picked and the picture size. How it is delivered (direct play or a conversion) is in the
 * `Playback` line, like a track's. It also keeps what plays for the player's info line (#276): [onChange] gets it at
 * each change, `null` when a new item starts.
 */
@UnstableApi
internal class VideoLogListener(
    private val log: AppLog,
    private val onChange: (VideoPlayback?) -> Unit = {},
) : AnalyticsListener {
    // Per queue item: the next episode's format is read ahead, before the transition to it (#276 review).
    private val byItem = mutableMapOf<Int, VideoPlayback>()

    // A decoder kept for the next item is not initialized again: it carries over.
    private var lastDecoder: String? = null

    override fun onMediaItemTransition(
        eventTime: AnalyticsListener.EventTime,
        mediaItem: androidx.media3.common.MediaItem?,
        reason: Int,
    ) {
        val now = eventTime.currentWindowIndex
        byItem.keys.retainAll { it >= now }
        onChange(byItem[now])
    }

    private fun update(
        eventTime: AnalyticsListener.EventTime,
        change: (VideoPlayback) -> VideoPlayback,
    ) {
        val item = eventTime.windowIndex
        val updated = change(byItem[item] ?: VideoPlayback(decoder = lastDecoder))
        byItem[item] = updated
        if (item == eventTime.currentWindowIndex) onChange(updated)
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        update(eventTime) {
            it.copy(
                mimeType = format.sampleMimeType,
                width = format.width,
                height = format.height,
                range = videoRangeOf(format.sampleMimeType, format.colorInfo?.colorTransfer ?: Format.NO_VALUE),
            )
        }
        log.info(
            TAG,
            "Format ${format.containerMimeType} ${format.sampleMimeType} ${format.codecs} ${format.width}x${format.height}",
        )
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        log.info(TAG, "Decoder $decoderName")
        lastDecoder = decoderName
        update(eventTime) { it.copy(decoder = decoderName) }
    }

    override fun onVideoSizeChanged(
        eventTime: AnalyticsListener.EventTime,
        videoSize: VideoSize,
    ) {
        log.info(TAG, "Output ${videoSize.width}x${videoSize.height}")
    }

    private companion object {
        const val TAG = "MqVideo"
    }
}
