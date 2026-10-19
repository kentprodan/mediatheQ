package io.github.kentprodan.mediatheq.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class BlurHashTest {
    @Test
    fun `decodes the same pixels as the reference implementation`() {
        // Expected values from the official TypeScript decoder (npm blurhash 2.0.5), decode(hash, 4, 3).
        val expected =
            intArrayOf(
                0x87A4B1,
                0xA1ADB1,
                0xB5B4AB,
                0xA0ACAE,
                0x7C9AA9,
                0x94949A,
                0xA49186,
                0x92989B,
                0x7C909A,
                0x908684,
                0xA38268,
                0x948C86,
            )

        val pixels = decodeBlurHash("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 4, 3)!!

        assertThat(pixels.size).isEqualTo(expected.size)
        pixels.forEachIndexed { i, argb ->
            assertThat(argb ushr 24).isEqualTo(0xFF)
            for (shift in intArrayOf(16, 8, 0)) {
                val diff = abs(((argb shr shift) and 255) - ((expected[i] shr shift) and 255))
                assertThat(diff).isAtMost(1)
            }
        }
    }

    @Test
    fun `invalid hashes return null`() {
        assertThat(decodeBlurHash("", 4, 3)).isNull()
        assertThat(decodeBlurHash("LEHV6nWB2yk8", 4, 3)).isNull()
        assertThat(decodeBlurHash("L\"HV6nWB2yk8pyo0adR*.7kCMdnj", 4, 3)).isNull()
    }
}
