package io.github.kentprodan.mediatheq.tv

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ProfilePickerTest {
    private val now = Instant.fromEpochSeconds(1_000_000)

    @Test
    fun `the picker is due after 4 hours away or on the first start, never after a quick return`() {
        assertThat(profilePickerDue(now - 4.hours, now)).isTrue()
        assertThat(profilePickerDue(null, now)).isTrue()
        assertThat(profilePickerDue(now - 3.hours - 59.minutes, now)).isFalse()
        assertThat(profilePickerDue(now - 2.minutes, now)).isFalse()
    }
}
