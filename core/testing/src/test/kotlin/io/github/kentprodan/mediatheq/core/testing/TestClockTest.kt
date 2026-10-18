package io.github.kentprodan.mediatheq.core.testing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TestClockTest {
    @Test
    fun `clock only moves when advanced`() {
        val clock = TestClock(Instant.fromEpochSeconds(100))
        assertThat(clock.now()).isEqualTo(Instant.fromEpochSeconds(100))
        clock.advanceBy(5.seconds)
        assertThat(clock.now()).isEqualTo(Instant.fromEpochSeconds(105))
    }
}
