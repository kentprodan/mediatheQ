package io.github.kentprodan.mediatheq.core.playback

import android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION
import android.view.Display.HdrCapabilities.HDR_TYPE_HDR10
import android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS
import android.view.Display.HdrCapabilities.HDR_TYPE_HLG
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.VideoQuality
import org.junit.Test

/** The HDR range table (#276, ADR 0050): display types x decoder profiles -> the declared `VideoRangeType`s. */
class VideoHdrTest {
    private val main10 = VideoHdrDecoders(hevcMain10 = true)
    private val pixel = VideoHdrDecoders(hevcMain10 = true, hevcHdr10Plus = true)
    private val withDv = pixel.copy(dolbyVision = true)
    private val hdrDisplay = setOf(HDR_TYPE_HDR10, HDR_TYPE_HLG, HDR_TYPE_HDR10_PLUS)

    @Test
    fun `an SDR display or no Main 10 decoder declares SDR and the SDR-based Dolby Vision only`() {
        assertThat(videoRangeTypes(emptySet(), withDv)).containsExactly("SDR", "DOVIWithSDR")
        assertThat(videoRangeTypes(hdrDisplay, VideoHdrDecoders())).containsExactly("SDR", "DOVIWithSDR")
    }

    @Test
    fun `an HDR display with Main 10 takes HDR10, HLG and Dolby Vision on an HDR10 or HLG base layer`() {
        assertThat(videoRangeTypes(hdrDisplay, main10))
            .containsExactly("SDR", "HDR10", "HLG", "DOVIWithHDR10", "DOVIWithHLG", "DOVIWithSDR")
    }

    @Test
    fun `HDR10+ needs both the display type and the decoder profile`() {
        assertThat(videoRangeTypes(hdrDisplay, pixel)).contains("HDR10Plus")
        assertThat(videoRangeTypes(setOf(HDR_TYPE_HDR10), pixel)).doesNotContain("HDR10Plus")
    }

    @Test
    fun `DOVI (profile 5) needs a Dolby Vision decoder and display, else it is tone-mapped`() {
        assertThat(videoRangeTypes(hdrDisplay, pixel)).doesNotContain("DOVI")
        assertThat(videoRangeTypes(hdrDisplay + HDR_TYPE_DOLBY_VISION, pixel)).doesNotContain("DOVI")
        assertThat(videoRangeTypes(hdrDisplay, withDv)).doesNotContain("DOVI")
        assertThat(videoRangeTypes(hdrDisplay + HDR_TYPE_DOLBY_VISION, withDv)).contains("DOVI")
    }

    @Test
    fun `the video profile carries the declared ranges`() {
        val ranges = videoRangeTypes(hdrDisplay, main10)

        val profile =
            DeviceProfileBuilder.video(emptySet(), setOf("video/hevc"), VideoQuality.Original, videoRangeTypes = ranges)

        assertThat(profile.videoRangeTypes).isEqualTo(ranges)
        assertThat(profile.videoCodecs).containsExactly("hevc")
    }
}
