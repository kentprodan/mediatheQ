package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import org.junit.Test

class EqualizerCurveTest {
    @Test
    fun `band edges lie halfway between centers, the last one at the top of hearing`() {
        val bands = EqualizerCurve.bands(AudioPreferences.EQ_BANDS_HZ, List(5) { 0f })

        assertThat(bands.map { it.cutoffHz.toInt() }).containsExactly(117, 457, 1809, 7099, 20_000).inOrder()
    }

    @Test
    fun `each band keeps its gain`() {
        val gains = listOf(6f, 4f, 0f, -2f, 1f)

        assertThat(EqualizerCurve.bands(AudioPreferences.EQ_BANDS_HZ, gains).map { it.gainDb }).isEqualTo(gains)
    }

    @Test
    fun `the input is lowered by the largest boost so nothing clips`() {
        assertThat(EqualizerCurve.preGainDb(listOf(6f, 4f, 0f, -2f, 1f))).isEqualTo(-6f)
    }

    @Test
    fun `cuts alone need no input gain`() {
        assertThat(EqualizerCurve.preGainDb(listOf(-3f, 0f, -6f, 0f, 0f))).isEqualTo(0f)
    }
}
