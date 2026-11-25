package io.github.kentprodan.mediatheq.feature.tv.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.DisplayMatch
import org.junit.Test

class DisplayModeChooserTest {
    // The Streamer's 4K list (and 1080p).
    private val uhd60 = TvDisplayMode(1, 3840, 2160, 60f)
    private val uhd5994 = TvDisplayMode(2, 3840, 2160, 59.94f)
    private val uhd50 = TvDisplayMode(3, 3840, 2160, 50f)
    private val uhd24 = TvDisplayMode(4, 3840, 2160, 24f)
    private val uhd23976 = TvDisplayMode(5, 3840, 2160, 23.976f)
    private val fhd60 = TvDisplayMode(6, 1920, 1080, 60f)
    private val fhd23976 = TvDisplayMode(7, 1920, 1080, 23.976f)
    private val modes = listOf(uhd60, uhd5994, uhd50, uhd24, uhd23976, fhd60, fhd23976)

    private fun choose(
        rate: Float,
        height: Int = 2160,
        viewer: TvDisplayMode = uhd60,
        match: DisplayMatch = DisplayMatch.Rate,
    ) = chooseDisplayMode(rate, height, modes, viewer, match)

    @Test
    fun `film plays at its own rate, never 60 Hz, and 1000-1001 rates are told apart`() {
        assertThat(choose(23.976f)).isEqualTo(uhd23976)
        assertThat(choose(24f)).isEqualTo(uhd24)
        // A loosely stated rate snaps to the standard one.
        assertThat(choose(23.98f)).isEqualTo(uhd23976)
    }

    @Test
    fun `25 and 29_97 prefer twice their rate, and no switch when the display is already right`() {
        assertThat(choose(25f)).isEqualTo(uhd50)
        assertThat(choose(29.97f)).isEqualTo(uhd5994)
        assertThat(sameDisplayMode(choose(59.94f)!!, current = uhd5994)).isTrue()
        assertThat(sameDisplayMode(choose(60f)!!, current = uhd60)).isTrue()
        // Same rate and height under another id (a 1080p 23.976 film on a 4K 23.976 display, rate only): no switch.
        assertThat(sameDisplayMode(TvDisplayMode(9, 4096, 2160, 23.976f), current = uhd23976)).isTrue()
        assertThat(sameDisplayMode(uhd24, current = uhd23976)).isFalse()
    }

    @Test
    fun `no rate or no fitting mode gives the viewer's mode back`() {
        assertThat(choose(0f)).isNull()
        assertThat(choose(17f)).isNull()
    }

    @Test
    fun `Off leaves the display, resolution matching picks the smallest mode that fits`() {
        assertThat(choose(23.976f, match = DisplayMatch.Off)).isNull()
        assertThat(choose(23.976f, height = 1080, match = DisplayMatch.RateAndResolution)).isEqualTo(fhd23976)
        // Rate only keeps the current resolution.
        assertThat(choose(23.976f, height = 1080)).isEqualTo(uhd23976)
    }
}
