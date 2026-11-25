package io.github.kentprodan.mediatheq.core.playback

import android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION
import android.view.Display.HdrCapabilities.HDR_TYPE_HDR10
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.google.common.truth.Truth.assertThat
import com.suyashbelekar.exoplayerhdrutils.video.transformers.DoviStrategy
import com.suyashbelekar.exoplayerhdrutils.video.transformers.Hdr10PlusStrategy
import org.junit.Test

class DolbyVisionCompatTest {
    private val dvTv = setOf(HDR_TYPE_HDR10, HDR_TYPE_DOLBY_VISION)
    private val profile8Only = VideoHdrDecoders(hevcMain10 = true, dolbyVision = true, dolbyVisionProfile8 = true)

    @Test
    fun `profile 7 stays on a profile 7 decoder, converts on a profile 8 one, else plays its HDR10 base`() {
        val p7 = profile8Only.copy(dolbyVisionProfile7 = true)
        assertThat(dolbyVisionCompat(p7, dvTv).profile7).isEqualTo(DoviStrategy.KEEP)
        assertThat(dolbyVisionCompat(profile8Only, dvTv).profile7).isEqualTo(DoviStrategy.CONVERT_TO_P8)
        // A Dolby Vision decoder with an HDR10-only display: no Dolby Vision to enter.
        assertThat(dolbyVisionCompat(profile8Only, setOf(HDR_TYPE_HDR10)).profile7).isEqualTo(DoviStrategy.DISCARD)
        assertThat(
            dolbyVisionCompat(VideoHdrDecoders(hevcMain10 = true), dvTv).profile7,
        ).isEqualTo(DoviStrategy.DISCARD)
    }

    @Test
    fun `HDR10+ in a Dolby Vision file goes only when Dolby Vision plays`() {
        assertThat(dolbyVisionCompat(profile8Only, dvTv).hdr10Plus).isEqualTo(Hdr10PlusStrategy.DISCARD)
        assertThat(dolbyVisionCompat(profile8Only, setOf(HDR_TYPE_HDR10)).hdr10Plus).isEqualTo(Hdr10PlusStrategy.KEEP)
    }

    @Test
    fun `the decoder sees a converted profile 7 as profile 8 and a stripped one as HEVC`() {
        val p7 =
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs("dvhe.07.06")
                .build()
        val p8 =
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs("dvh1.08.06")
                .build()

        val convert = DolbyVisionCompat(DoviStrategy.CONVERT_TO_P8, Hdr10PlusStrategy.DISCARD)
        assertThat(convert.advertise(p7).codecs).isEqualTo("dvhe.08.06")
        assertThat(convert.advertise(p8)).isSameInstanceAs(p8)

        val discard = DolbyVisionCompat(DoviStrategy.DISCARD, Hdr10PlusStrategy.KEEP)
        assertThat(discard.advertise(p7).sampleMimeType).isEqualTo(MimeTypes.VIDEO_H265)
        assertThat(discard.advertise(p7).codecs).isNull()

        assertThat(DolbyVisionCompat(DoviStrategy.KEEP, Hdr10PlusStrategy.KEEP).advertise(p7)).isSameInstanceAs(p7)
    }

    @Test
    fun `profile 7 is declared wherever it can play, converted or as HDR10`() {
        assertThat(videoRangeTypes(setOf(HDR_TYPE_HDR10), VideoHdrDecoders(hevcMain10 = true))).contains("DOVIWithEL")
        assertThat(videoRangeTypes(emptySet(), VideoHdrDecoders(hevcMain10 = true))).doesNotContain("DOVIWithEL")
        assertThat(videoRangeTypes(dvTv, profile8Only)).containsAtLeast("DOVIWithEL", "DOVIWithHDR10Plus")
    }
}
