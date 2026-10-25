package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import org.junit.Test

class DeviceProfileBuilderTest {
    private val pixel =
        setOf("audio/flac", "audio/mpeg", "audio/mp4a-latm", "audio/opus", "audio/vorbis", "audio/eac3", "audio/ac3")

    @Test
    fun `original quality has no bitrate cap, so FLAC plays as it is`() {
        val profile = DeviceProfileBuilder.build(pixel, StreamingQuality.Original)

        assertThat(profile.maxStreamingBitrate).isNull()
        assertThat(profile.directPlayCodecs).contains("flac")
        assertThat(profile.directPlayContainers).contains("flac")
    }

    @Test
    fun `256 kbps caps the stream and converts to Opus 256 in HLS segments`() {
        val profile = DeviceProfileBuilder.build(pixel, StreamingQuality.Kbps256)

        assertThat(profile.maxStreamingBitrate).isEqualTo(256_000)
        assertThat(profile.transcoding).isEqualTo(DeviceProfileBuilder.OPUS_256)
        assertThat(profile.transcoding.bitrate).isAtMost(256_000)
        assertThat(profile.transcoding.segmented).isTrue()
        assertThat(profile.transcoding.container).isEqualTo("mp4")
    }

    @Test
    fun `codecs without a decoder are left for the server to convert`() {
        val profile = DeviceProfileBuilder.build(setOf("audio/mpeg"), StreamingQuality.Original)

        assertThat(profile.directPlayCodecs).containsExactly("mp3", "pcm_s16le", "pcm_s24le")
    }

    @Test
    fun `E-AC-3 needs a JOC decoder, a plain E-AC-3 decoder is not enough for Atmos music`() {
        assertThat(DeviceProfileBuilder.build(pixel, StreamingQuality.Original).directPlayCodecs).doesNotContain("eac3")
        assertThat(
            DeviceProfileBuilder.build(pixel + "audio/eac3-joc", StreamingQuality.Original).directPlayCodecs,
        ).contains("eac3")
    }

    @Test
    fun `TrueHD is never declared on the phone, even with a TrueHD decoder (passthrough comes with F2)`() {
        val profile = DeviceProfileBuilder.build(pixel + "audio/true-hd" + "audio/eac3-joc", StreamingQuality.Original)

        assertThat(profile.directPlayCodecs).doesNotContain("truehd")
    }

    @Test
    fun `PCM in WAV plays without a platform decoder`() {
        assertThat(DeviceProfileBuilder.build(emptySet(), StreamingQuality.Original).directPlayCodecs)
            .containsExactly("pcm_s16le", "pcm_s24le")
    }

    @Test
    fun `128 kbps converts to Opus 128, not 256`() {
        val profile = DeviceProfileBuilder.build(pixel, StreamingQuality.Kbps128)

        assertThat(profile.maxStreamingBitrate).isEqualTo(128_000)
        assertThat(profile.transcoding).isEqualTo(DeviceProfileBuilder.opus(128_000))
    }

    @Test
    fun `original quality still converts formats the device cannot decode, to Opus 256`() {
        assertThat(DeviceProfileBuilder.build(pixel, StreamingQuality.Original).transcoding)
            .isEqualTo(DeviceProfileBuilder.OPUS_256)
    }
}
