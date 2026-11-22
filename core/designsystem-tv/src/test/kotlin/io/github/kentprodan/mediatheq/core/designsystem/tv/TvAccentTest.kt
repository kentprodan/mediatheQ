package io.github.kentprodan.mediatheq.core.designsystem.tv

import com.google.common.truth.Truth.assertThat
import com.materialkolor.hct.Hct
import org.junit.Test

class TvAccentTest {
    @Test
    fun `a red poster gives a red accent (#517)`() {
        val red = IntArray(64 * 64) { 0xFFC62828.toInt() }

        val seed = posterSeedColor(red)

        assertThat(seed).isNotNull()
        // Red's hue sits around 25 degrees.
        assertThat(Hct.fromInt(seed!!).hue).isWithin(15.0).of(Hct.fromInt(0xFFC62828.toInt()).hue)
    }

    @Test
    fun `a grey or empty picture has no accent, so the page keeps Iris (#517)`() {
        assertThat(posterSeedColor(IntArray(64 * 64) { 0xFF808080.toInt() })).isNull()
        assertThat(posterSeedColor(IntArray(0))).isNull()
    }

    @Test
    fun `the accent theme uses the seed for the primary roles and stays dark (#517)`() {
        val scheme = tvColorScheme(0xFFC62828.toInt())
        val iris = tvColorScheme(IRIS_SEED)

        assertThat(scheme.primary).isNotEqualTo(iris.primary)
        assertThat(scheme.background.luminanceApprox()).isLessThan(0.1f)
    }

    private fun androidx.compose.ui.graphics.Color.luminanceApprox() = (red + green + blue) / 3f
}
