package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.StreamingQuality.Original
import io.github.kentprodan.mediatheq.core.model.TranscodingTarget
import io.github.kentprodan.mediatheq.core.model.VideoQuality
import org.junit.Test

class DeviceProfileBuilderTest {
    private val none = emptySet<String>()
    private val pixel =
        setOf("audio/flac", "audio/mpeg", "audio/mp4a-latm", "audio/opus", "audio/vorbis", "audio/eac3", "audio/ac3")

    @Test
    fun `original quality has no bitrate cap, so FLAC plays as it is`() {
        val profile = DeviceProfileBuilder.build(pixel, none, StreamingQuality.Original)

        assertThat(profile.maxStreamingBitrate).isNull()
        assertThat(profile.directPlayCodecs).contains("flac")
        assertThat(profile.directPlayContainers).contains("flac")
    }

    @Test
    fun `256 kbps caps the stream and converts to Opus 256 in HLS segments`() {
        val profile = DeviceProfileBuilder.build(pixel, none, StreamingQuality.Kbps256)

        assertThat(profile.maxStreamingBitrate).isEqualTo(256_000)
        assertThat(profile.transcoding).isEqualTo(DeviceProfileBuilder.OPUS_256)
        assertThat(profile.transcoding.bitrate).isAtMost(256_000)
        assertThat(profile.transcoding.segmented).isTrue()
        assertThat(profile.transcoding.container).isEqualTo("mp4")
    }

    @Test
    fun `codecs without a decoder are left for the server to convert`() {
        val profile = DeviceProfileBuilder.build(setOf("audio/mpeg"), none, StreamingQuality.Original)

        assertThat(profile.directPlayCodecs).containsExactly("mp3", "pcm_s16le", "pcm_s24le")
    }

    @Test
    fun `E-AC-3 needs a JOC decoder, a plain E-AC-3 decoder is not enough for Atmos music`() {
        assertThat(
            DeviceProfileBuilder.build(pixel, none, StreamingQuality.Original).directPlayCodecs,
        ).doesNotContain("eac3")
        assertThat(
            DeviceProfileBuilder.build(pixel + "audio/eac3-joc", none, StreamingQuality.Original).directPlayCodecs,
        ).contains("eac3")
    }

    @Test
    fun `TrueHD is never declared without passthrough, even with a TrueHD decoder`() {
        val profile = DeviceProfileBuilder.build(pixel + "audio/true-hd" + "audio/eac3-joc", none, Original)

        assertThat(profile.directPlayCodecs).doesNotContain("truehd")
    }

    @Test
    fun `TrueHD is declared only when the route passes TrueHD through`() {
        assertThat(DeviceProfileBuilder.build(pixel, setOf(TRUEHD), Original).directPlayCodecs).contains("truehd")
        assertThat(DeviceProfileBuilder.build(pixel, setOf(JOC, EAC3), Original).directPlayCodecs)
            .doesNotContain("truehd")
    }

    @Test
    fun `E-AC-3 without JOC decoder and without JOC passthrough is left for the server`() {
        assertThat(DeviceProfileBuilder.build(pixel, none, Original).directPlayCodecs).doesNotContain("eac3")
    }

    @Test
    fun `E-AC-3 with a JOC decoder and no passthrough plays as it is`() {
        assertThat(DeviceProfileBuilder.build(pixel + JOC, none, Original).directPlayCodecs).contains("eac3")
    }

    @Test
    fun `E-AC-3 with JOC passthrough and no JOC decoder plays as it is`() {
        assertThat(DeviceProfileBuilder.build(pixel, setOf(JOC), Original).directPlayCodecs).contains("eac3")
    }

    @Test
    fun `E-AC-3 with both a JOC decoder and JOC passthrough plays as it is`() {
        assertThat(DeviceProfileBuilder.build(pixel + JOC, setOf(JOC), Original).directPlayCodecs).contains("eac3")
    }

    @Test
    fun `plain E-AC-3 passthrough is not enough for Atmos music`() {
        assertThat(DeviceProfileBuilder.build(pixel, setOf(EAC3), Original).directPlayCodecs).doesNotContain("eac3")
    }

    @Test
    fun `an empty passthrough set is today's profile, byte for byte`() {
        for (quality in StreamingQuality.entries) {
            assertThat(DeviceProfileBuilder.build(pixel, none, quality).directPlayCodecs)
                .containsExactly("flac", "mp3", "aac", "opus", "vorbis", "ac3", "pcm_s16le", "pcm_s24le")
                .inOrder()
            assertThat(DeviceProfileBuilder.build(pixel + JOC, none, quality).directPlayCodecs)
                .containsExactly("flac", "mp3", "aac", "opus", "vorbis", "ac3", "eac3", "pcm_s16le", "pcm_s24le")
                .inOrder()
        }
    }

    @Test
    fun `PCM in WAV plays without a platform decoder`() {
        assertThat(DeviceProfileBuilder.build(emptySet(), none, StreamingQuality.Original).directPlayCodecs)
            .containsExactly("pcm_s16le", "pcm_s24le")
    }

    @Test
    fun `128 kbps converts to Opus 128, not 256`() {
        val profile = DeviceProfileBuilder.build(pixel, none, StreamingQuality.Kbps128)

        assertThat(profile.maxStreamingBitrate).isEqualTo(128_000)
        assertThat(profile.transcoding).isEqualTo(DeviceProfileBuilder.opus(128_000))
    }

    @Test
    fun `a download plays what the device plays as it is, and converts the rest to one Opus 256 Ogg file`() {
        val download = DeviceProfileBuilder.download(pixel)
        val original = DeviceProfileBuilder.build(pixel, none, StreamingQuality.Original)

        assertThat(download.directPlayCodecs).isEqualTo(original.directPlayCodecs)
        assertThat(download.maxStreamingBitrate).isNull()
        assertThat(download.transcoding)
            .isEqualTo(TranscodingTarget(container = "ogg", codec = "opus", bitrate = 256_000, maxChannels = 2))
        assertThat(download.transcoding.segmented).isFalse()
    }

    @Test
    fun `a download at a bitrate never plays the original and converts to one Opus Ogg file at that bitrate`() {
        for (quality in listOf(StreamingQuality.Kbps320, StreamingQuality.Kbps256, StreamingQuality.Kbps192)) {
            val download = DeviceProfileBuilder.download(pixel, quality)

            assertThat(download.directPlayContainers).isEmpty()
            assertThat(download.directPlayCodecs).isEmpty()
            assertThat(download.maxStreamingBitrate).isEqualTo(quality.bitrate)
            assertThat(download.transcoding)
                .isEqualTo(
                    TranscodingTarget(container = "ogg", codec = "opus", bitrate = quality.bitrate!!, maxChannels = 2),
                )
            assertThat(download.transcoding.segmented).isFalse()
        }
    }

    @Test
    fun `a download at Original is today's download profile`() {
        assertThat(DeviceProfileBuilder.download(pixel, StreamingQuality.Original))
            .isEqualTo(DeviceProfileBuilder.download(pixel))
    }

    @Test
    fun `original quality still converts formats the device cannot decode, to Opus 256`() {
        assertThat(DeviceProfileBuilder.build(pixel, none, StreamingQuality.Original).transcoding)
            .isEqualTo(DeviceProfileBuilder.OPUS_256)
    }

    private val h264 = setOf("video/avc")
    private val h264Hevc = h264 + "video/hevc"

    @Test
    fun `a video profile lists only the video codecs the device decodes`() {
        assertThat(DeviceProfileBuilder.video(pixel, h264, VideoQuality.Original).videoCodecs).containsExactly("h264")
        assertThat(DeviceProfileBuilder.video(pixel, h264Hevc, VideoQuality.Original).videoCodecs)
            .containsExactly("h264", "hevc")
            .inOrder()
        assertThat(DeviceProfileBuilder.video(pixel, h264Hevc + "video/av01", VideoQuality.Original).videoCodecs)
            .containsExactly("h264", "hevc", "av1")
            .inOrder()
    }

    @Test
    fun `a video profile plays video containers with the music audio rule inside`() {
        val profile = DeviceProfileBuilder.video(pixel, h264, VideoQuality.Original)

        assertThat(profile.directPlayContainers).containsExactly("mp4", "m4v", "mkv", "webm", "ts")
        assertThat(profile.directPlayCodecs).containsAtLeast("aac", "opus", "ac3")
        // A plain E-AC-3 decoder is not enough (JOC rule), and phones never get TrueHD.
        assertThat(profile.directPlayCodecs).containsNoneOf("eac3", "truehd")
    }

    @Test
    fun `original video quality has no cap, 480p caps the bitrate and the height`() {
        val original = DeviceProfileBuilder.video(pixel, h264, VideoQuality.Original)
        val p480 = DeviceProfileBuilder.video(pixel, h264, VideoQuality.P480)

        assertThat(original.maxStreamingBitrate).isNull()
        assertThat(original.maxHeight).isNull()
        assertThat(p480.maxStreamingBitrate).isEqualTo(1_500_000)
        assertThat(p480.maxHeight).isEqualTo(480)
    }

    @Test
    fun `videos convert to HLS fMP4, HEVC first, AAC or Opus up to 5_1`() {
        val transcoding = DeviceProfileBuilder.video(pixel, h264, VideoQuality.P480).transcoding

        assertThat(transcoding.segmented).isTrue()
        assertThat(transcoding.container).isEqualTo("mp4")
        assertThat(transcoding.videoCodec).isEqualTo("hevc,h264")
        assertThat(transcoding.codec).isEqualTo("aac,opus")
        assertThat(transcoding.maxChannels).isEqualTo(6)
    }
}

private const val JOC = "audio/eac3-joc"
private const val EAC3 = "audio/eac3"
private const val TRUEHD = "audio/true-hd"
