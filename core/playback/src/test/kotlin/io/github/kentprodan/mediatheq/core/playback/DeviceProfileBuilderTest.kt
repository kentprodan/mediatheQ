package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceProfileBuilderTest {
    private val pixel =
        setOf("audio/flac", "audio/mpeg", "audio/mp4a-latm", "audio/opus", "audio/vorbis", "audio/eac3", "audio/ac3")

    @Test
    fun `unmetered network has no bitrate cap, so FLAC plays in original quality`() {
        val profile = DeviceProfileBuilder.build(pixel, metered = false)

        assertThat(profile.maxStreamingBitrate).isNull()
        assertThat(profile.directPlayCodecs).contains("flac")
        assertThat(profile.directPlayContainers).contains("flac")
    }

    @Test
    fun `metered network caps at 256 kbps and converts to Opus in Ogg`() {
        val profile = DeviceProfileBuilder.build(pixel, metered = true)

        assertThat(profile.maxStreamingBitrate).isEqualTo(256_000)
        assertThat(profile.transcoding).isEqualTo(DeviceProfileBuilder.OPUS_256)
        assertThat(profile.transcoding.bitrate).isAtMost(256_000)
    }

    @Test
    fun `codecs without a decoder are left for the server to convert`() {
        val profile = DeviceProfileBuilder.build(setOf("audio/mpeg"), metered = false)

        assertThat(profile.directPlayCodecs).containsExactly("mp3", "pcm_s16le", "pcm_s24le")
    }

    @Test
    fun `E-AC-3 needs a JOC decoder, a plain E-AC-3 decoder is not enough for Atmos music`() {
        assertThat(DeviceProfileBuilder.build(pixel, metered = false).directPlayCodecs).doesNotContain("eac3")
        assertThat(
            DeviceProfileBuilder.build(pixel + "audio/eac3-joc", metered = false).directPlayCodecs,
        ).contains("eac3")
    }

    @Test
    fun `without any E-AC-3 decoder, Atmos E-AC-3 is left for the server to convert`() {
        assertThat(
            DeviceProfileBuilder.build(setOf("audio/flac"), metered = false).directPlayCodecs,
        ).doesNotContain("eac3")
    }

    @Test
    fun `TrueHD is never declared on the phone, even with a TrueHD decoder (passthrough comes with F2)`() {
        val profile = DeviceProfileBuilder.build(pixel + "audio/true-hd" + "audio/eac3-joc", metered = false)

        assertThat(profile.directPlayCodecs).doesNotContain("truehd")
    }

    @Test
    fun `PCM in WAV plays without a platform decoder`() {
        assertThat(DeviceProfileBuilder.build(emptySet(), metered = false).directPlayCodecs)
            .containsExactly("pcm_s16le", "pcm_s24le")
    }
}
