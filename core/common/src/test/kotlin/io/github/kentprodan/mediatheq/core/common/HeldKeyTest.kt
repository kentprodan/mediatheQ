package io.github.kentprodan.mediatheq.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeldKeyTest {
    private fun HeldKey.presses(
        times: List<Long>,
        forward: Boolean = true,
    ) = times.map { press(forward, it) }

    @Test
    fun `system repeats every 50 ms are one hold, one seek per 150 ms`() {
        val results = HeldKey().presses((0L..400L step 50).toList())

        assertThat(results).containsExactly(0, null, null, 3, null, null, 6, null, null).inOrder()
    }

    @Test
    fun `separate presses as fast as a remote's own repeat are one hold too (#353)`() {
        val results = HeldKey().presses(listOf(0L, 200L, 400L, 600L))

        assertThat(results).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `slow taps each seek as a new press`() {
        assertThat(HeldKey().presses(listOf(0L, 500L, 1_000L))).containsExactly(0, 0, 0).inOrder()
    }

    @Test
    fun `the other direction starts a new hold`() {
        val key = HeldKey()
        key.presses(listOf(0L, 50L, 100L, 150L))

        assertThat(key.press(forward = false, atMs = 200L)).isEqualTo(0)
    }

    @Test
    fun `another item starts a new hold`() {
        val key = HeldKey()
        assertThat(key.press(true, 0L, item = "a")).isEqualTo(0)
        assertThat(key.press(true, 200L, item = "a")).isEqualTo(1)

        assertThat(key.press(true, 400L, item = "b")).isEqualTo(0)
    }
}
