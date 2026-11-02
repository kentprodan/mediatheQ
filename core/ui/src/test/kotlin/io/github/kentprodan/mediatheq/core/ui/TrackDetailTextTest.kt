package io.github.kentprodan.mediatheq.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class TrackDetailTextTest {
    private fun track(format: AudioFormat? = null) =
        Track(MediaId(ServerId("s"), "t1"), "Drum", emptyList(), 245.seconds, audioFormat = format)

    @Test
    fun `duration, codec and bitrate read as text`() {
        val track = track(AudioFormat("flac", bitrateKbps = 1411))

        assertThat(trackDetailText(track, TrackDetail.Duration)).isEqualTo("4:05")
        assertThat(trackDetailText(track, TrackDetail.Codec)).isEqualTo("FLAC")
        assertThat(trackDetailText(track, TrackDetail.Bitrate)).isEqualTo("1411 kbps")
        assertThat(trackDetailText(track, TrackDetail.CodecAndBitrate)).isEqualTo("FLAC · 1411 kbps")
    }

    @Test
    fun `codec and bitrate keep what is known and are absent without a format`() {
        assertThat(trackDetailText(track(AudioFormat("opus")), TrackDetail.CodecAndBitrate)).isEqualTo("OPUS")
        assertThat(trackDetailText(track(AudioFormat("", bitrateKbps = 256)), TrackDetail.CodecAndBitrate))
            .isEqualTo("256 kbps")
        assertThat(trackDetailText(track(), TrackDetail.Codec)).isNull()
        assertThat(trackDetailText(track(), TrackDetail.CodecAndBitrate)).isNull()
    }

    @Test
    fun `icon details are not text`() {
        listOf(TrackDetail.Favorite, TrackDetail.Rating, TrackDetail.PlayCount, TrackDetail.Offline).forEach {
            assertThat(trackDetailText(track(AudioFormat("flac")), it)).isNull()
        }
    }
}
