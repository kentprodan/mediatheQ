package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.VideoRange
import io.github.kentprodan.mediatheq.core.testing.FakeAppLog
import org.junit.Test

@UnstableApi
class VideoLogListenerTest {
    private val heard = mutableListOf<VideoPlayback?>()
    private val listener = VideoLogListener(FakeAppLog()) { heard += it }

    // An event about [item] while [current] plays.
    private fun at(
        item: Int,
        current: Int,
    ) = AnalyticsListener.EventTime(0, Timeline.EMPTY, item, null, 0, Timeline.EMPTY, current, null, 0, 0)

    private fun format(
        mime: String,
        transfer: Int,
    ) = Format
        .Builder()
        .setSampleMimeType(mime)
        .setWidth(3840)
        .setHeight(2160)
        .setColorInfo(ColorInfo.Builder().setColorTransfer(transfer).build())
        .build()

    @Test
    fun `the next episode's format read ahead is what shows after the transition, with the decoder kept`() {
        listener.onVideoInputFormatChanged(at(0, 0), format("video/hevc", C.COLOR_TRANSFER_SDR), null)
        listener.onVideoDecoderInitialized(at(0, 0), "c2.hevc", 0, 0)
        // Read ahead while the first plays: not shown yet.
        listener.onVideoInputFormatChanged(at(1, 0), format("video/hevc", C.COLOR_TRANSFER_ST2084), null)
        assertThat(heard.last()?.range).isEqualTo(VideoRange.Sdr)

        listener.onMediaItemTransition(at(1, 1), null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        assertThat(heard.last()?.range).isEqualTo(VideoRange.Hdr10)
        assertThat(heard.last()?.decoder).isEqualTo("c2.hevc")
    }

    @Test
    fun `an item without video (music) shows none`() {
        listener.onVideoInputFormatChanged(at(0, 0), format("video/avc", C.COLOR_TRANSFER_SDR), null)

        listener.onMediaItemTransition(at(1, 1), null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        assertThat(heard.last()).isNull()
    }
}
