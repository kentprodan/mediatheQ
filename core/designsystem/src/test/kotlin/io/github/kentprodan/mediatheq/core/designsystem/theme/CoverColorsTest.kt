package io.github.kentprodan.mediatheq.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import com.materialkolor.hct.Hct
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.abs

class CoverColorsTest {
    private val red = 0xFFD32F2F.toInt()
    private val teal = 0xFF00897B.toInt()

    private fun hueOf(color: Color) = Hct.fromInt(color.value.shr(32).toInt()).hue

    private fun hueDistance(
        a: Double,
        b: Double,
    ) = abs(a - b).let { minOf(it, 360 - it) }

    @Test
    fun `the seed of a mostly red cover is red`() {
        val pixels = IntArray(900) { if (it < 700) red else teal }

        val seed = coverSeedColor(pixels)!!

        assertThat(hueDistance(Hct.fromInt(seed).hue, Hct.fromInt(red).hue)).isLessThan(15.0)
    }

    @Test
    fun `a grey cover has no seed, so Iris stays`() {
        assertThat(coverSeedColor(IntArray(400) { 0xFF808080.toInt() })).isNull()
        assertThat(coverColorsOf(IntArray(400) { 0xFF808080.toInt() })).isNull()
        assertThat(coverSeedColor(IntArray(0))).isNull()
    }

    @Test
    fun `the schemes keep the cover's hue, light on light and dark on dark`() {
        val colors = coverColorsOf(IntArray(400) { teal })!!

        assertThat(hueDistance(hueOf(colors.light.primary), Hct.fromInt(teal).hue)).isLessThan(20.0)
        assertThat(colors.light.surface.luminance()).isGreaterThan(0.5f)
        assertThat(colors.dark.surface.luminance()).isLessThan(0.1f)
        assertThat(colors.light.onPrimary).isNotEqualTo(colors.light.primary)
    }

    @Test
    fun `the same pixels always give the same scheme`() {
        val pixels = IntArray(900) { if (it % 3 == 0) red else teal }

        // ColorScheme has no value equality; its toString lists every role.
        assertThat(coverColorsOf(pixels)!!.dark.toString()).isEqualTo(coverColorsOf(pixels)!!.dark.toString())
    }

    @Test
    fun `blending goes from one scheme to the other`() {
        val a = coverColorScheme(red, isDark = false)
        val b = coverColorScheme(teal, isDark = false)

        assertThat(a.lerp(b, 0f).toString()).isEqualTo(a.toString())
        assertThat(a.lerp(b, 1f).toString()).isEqualTo(b.toString())
        assertThat(a.lerp(b, 0.5f).primary).isNotEqualTo(a.primary)
    }

    @Test
    fun `a cached cover is never computed again`() =
        runTest {
            val cache = CoverColorCache()
            var computed = 0
            val colors = coverColorsOf(IntArray(400) { teal })

            cache.getOrCompute("album:tag") {
                computed++
                colors
            }
            val again =
                cache.getOrCompute("album:tag") {
                    computed++
                    null
                }

            assertThat(computed).isEqualTo(1)
            assertThat(again).isSameInstanceAs(colors)
            assertThat(cache["album:tag"]).isSameInstanceAs(colors)
        }

    @Test
    fun `a failure is not cached, so the next request tries again`() =
        runTest {
            val cache = CoverColorCache()
            var computed = 0

            cache.getOrCompute("album:tag") {
                computed++
                null
            }
            cache.getOrCompute("album:tag") {
                computed++
                null
            }

            assertThat(computed).isEqualTo(2)
            assertThat(cache["album:tag"]).isNull()
        }

    @Test
    fun `the least recently used cover is evicted first`() =
        runTest {
            val cache = CoverColorCache(maxSize = 2)
            val colors = coverColorsOf(IntArray(400) { teal })

            cache.getOrCompute("a") { colors }
            cache.getOrCompute("b") { colors }
            cache["a"]
            cache.getOrCompute("c") { colors }

            assertThat(cache["a"]).isNotNull()
            assertThat(cache["b"]).isNull()
            assertThat(cache["c"]).isNotNull()
        }
}
