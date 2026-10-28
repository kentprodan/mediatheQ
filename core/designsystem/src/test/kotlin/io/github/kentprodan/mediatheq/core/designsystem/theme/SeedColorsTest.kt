package io.github.kentprodan.mediatheq.core.designsystem.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SeedColorsTest {
    @Test
    fun `a seed made from a hue has that hue, and is opaque`() {
        listOf(0f, 95f, 200f, 310f).forEach { hue ->
            val seed = seedOfHue(hue)

            assertThat(seedHue(seed)).isWithin(2f).of(hue)
            assertThat(seed ushr 24).isEqualTo(0xFF)
        }
    }

    @Test
    fun `the presets start with Iris and are all different`() {
        assertThat(MqSeedPresets.first()).isEqualTo(MqThemeOptions.IRIS_SEED)
        assertThat(MqSeedPresets.toSet()).hasSize(MqSeedPresets.size)
    }
}
