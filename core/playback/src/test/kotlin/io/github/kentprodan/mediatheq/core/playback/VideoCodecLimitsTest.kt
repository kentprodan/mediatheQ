package io.github.kentprodan.mediatheq.core.playback

import android.media.MediaCodecInfo.CodecProfileLevel
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.VideoCodecLimit
import io.github.kentprodan.mediatheq.core.model.VideoQuality
import org.junit.Test

class VideoCodecLimitsTest {
    @Test
    fun `decoder levels become the server's level_idc, main and high tier alike`() {
        assertThat(levelIdc("video/avc", CodecProfileLevel.AVCLevel41)).isEqualTo(41)
        assertThat(levelIdc("video/avc", CodecProfileLevel.AVCLevel52)).isEqualTo(52)
        assertThat(levelIdc("video/hevc", CodecProfileLevel.HEVCMainTierLevel51)).isEqualTo(153)
        assertThat(levelIdc("video/hevc", CodecProfileLevel.HEVCHighTierLevel51)).isEqualTo(153)
        assertThat(levelIdc("video/hevc", CodecProfileLevel.HEVCMainTierLevel62)).isEqualTo(186)
        assertThat(levelIdc("video/av01", CodecProfileLevel.AV1Level51)).isNull()
    }

    @Test
    fun `High 10 and Main 10 are named only when the decoder lists them`() {
        assertThat(profileNames("video/avc", setOf(CodecProfileLevel.AVCProfileHigh))).doesNotContain("high 10")
        assertThat(profileNames("video/avc", setOf(CodecProfileLevel.AVCProfileHigh10))).contains("high 10")
        assertThat(profileNames("video/hevc", setOf(CodecProfileLevel.HEVCProfileMain))).containsExactly("main")
        assertThat(profileNames("video/hevc", setOf(CodecProfileLevel.HEVCProfileMain10HDR10)))
            .containsExactly("main", "main 10")
    }

    @Test
    fun `the video profile carries the limits, a conversion does not`() {
        val limits = listOf(VideoCodecLimit("hevc", 3840, 2160, 153, listOf("main", "main 10")))

        val direct =
            DeviceProfileBuilder.video(
                emptySet(),
                setOf("video/hevc"),
                VideoQuality.Original,
                codecLimits = limits,
            )
        val converted =
            DeviceProfileBuilder.video(
                emptySet(),
                setOf("video/hevc"),
                VideoQuality.Original,
                converted = true,
                codecLimits = limits,
            )

        assertThat(direct.videoCodecLimits).isEqualTo(limits)
        assertThat(converted.videoCodecLimits).isEmpty()
    }
}
