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
 * `Playback` line, like a track's.
 */
@UnstableApi
internal class VideoLogListener(
    private val log: AppLog,
) : AnalyticsListener {
    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
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
