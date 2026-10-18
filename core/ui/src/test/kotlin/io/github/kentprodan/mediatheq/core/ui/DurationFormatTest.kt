package io.github.kentprodan.mediatheq.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationFormatTest {
    @Test
    fun `under an hour is m colon ss`() {
        assertThat(formatDuration(3.minutes + 5.seconds)).isEqualTo("3:05")
        assertThat(formatDuration(Duration.ZERO)).isEqualTo("0:00")
    }

    @Test
    fun `an hour or more is h colon mm colon ss`() {
        assertThat(formatDuration(1.hours + 2.minutes + 3.seconds)).isEqualTo("1:02:03")
    }

    @Test
    fun `milliseconds are truncated`() {
        assertThat(formatDuration(59.seconds + 999.toDuration())).isEqualTo("0:59")
    }

    private fun Int.toDuration() = kotlin.time.Duration.parse("${this}ms")
}
