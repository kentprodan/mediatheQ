package io.github.kentprodan.mediatheq.feature.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.VideoRange
import io.github.kentprodan.mediatheq.core.playback.VideoPlayback
import org.junit.Test

class VideoInfoLineTest {
    @Test
    fun `the info line says codec, size, range, method and decoder, leaving out what is unknown`() {
        val hdr =
            VideoPlayback("video/hevc", 3840, 2160, VideoRange.Hdr10, PlayMethod.DirectPlay, "c2.exynos.hevc.decoder")

        assertThat(
            infoLine(hdr, "Direct play"),
        ).isEqualTo("HEVC 3840 × 2160 · HDR10 · Direct play · c2.exynos.hevc.decoder")
        assertThat(infoLine(VideoPlayback(), null)).isEqualTo("SDR")
    }
}
