package io.github.kentprodan.mediatheq.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.source.EmptySampleStream
import androidx.media3.exoplayer.source.MediaSource
import com.google.common.truth.Truth.assertThat
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.type.AssRenderType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AssClockRendererTest {
    private val handler = AssHandler(AssRenderType.OVERLAY_CANVAS)
    private val renderer = AssClockRenderer(handler)

    @Test
    fun `libass gets the period's own time, also for the second item of a playlist`() {
        // The second episode starts after a first of 40 minutes: its renderer offset is past the initial one.
        val offsetUs = 1_000_000_000_000L + 2_400_000_000L
        renderer.enable(
            RendererConfiguration.DEFAULT,
            arrayOf<Format>(),
            EmptySampleStream(),
            offsetUs,
            false,
            true,
            0L,
            offsetUs,
            MediaSource.MediaPeriodId(Any()),
        )

        renderer.render(offsetUs + 5_000_000L, 0L)

        assertThat(handler.videoTime).isEqualTo(5_000_000L)
    }
}
