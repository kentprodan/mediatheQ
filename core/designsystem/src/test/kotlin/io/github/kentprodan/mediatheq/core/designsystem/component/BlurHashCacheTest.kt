package io.github.kentprodan.mediatheq.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlurHashCacheTest {
    @Test
    fun `a hash is decoded once, then the same bitmap comes back`() {
        val first = blurHashBitmap("LEHV6nWB2yk8pyo0adR*.7kCMdnj")

        assertThat(first).isNotNull()
        assertThat(blurHashBitmap("LEHV6nWB2yk8pyo0adR*.7kCMdnj")).isSameInstanceAs(first)
    }

    @Test
    fun `a broken hash is no placeholder`() {
        assertThat(blurHashBitmap("x")).isNull()
    }
}
