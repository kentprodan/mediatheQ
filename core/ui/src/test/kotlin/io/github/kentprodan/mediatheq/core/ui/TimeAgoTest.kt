package io.github.kentprodan.mediatheq.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TimeAgoTest {
    @Test
    fun `under a minute is just now, also a clock set back`() {
        assertThat(timeAgo(59.seconds)).isEqualTo(TimeAgoUnit.JustNow to 0)
        assertThat(timeAgo((-5).minutes)).isEqualTo(TimeAgoUnit.JustNow to 0)
    }

    @Test
    fun `the largest whole unit is used`() {
        assertThat(timeAgo(1.minutes)).isEqualTo(TimeAgoUnit.Minutes to 1)
        assertThat(timeAgo(59.minutes + 59.seconds)).isEqualTo(TimeAgoUnit.Minutes to 59)
        assertThat(timeAgo(5.hours + 30.minutes)).isEqualTo(TimeAgoUnit.Hours to 5)
        assertThat(timeAgo(3.days + 23.hours)).isEqualTo(TimeAgoUnit.Days to 3)
    }
}
