package io.github.kentprodan.mediatheq.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FrequencyFormatTest {
    @Test
    fun `below a kilohertz is in hertz`() {
        assertThat(formatFrequency(60)).isEqualTo("60 Hz")
        assertThat(formatFrequency(910)).isEqualTo("910 Hz")
    }

    @Test
    fun `from a kilohertz one decimal shows only when needed`() {
        assertThat(formatFrequency(44_100)).isEqualTo("44.1 kHz")
        assertThat(formatFrequency(96_000)).isEqualTo("96 kHz")
        assertThat(formatFrequency(3_600)).isEqualTo("3.6 kHz")
        assertThat(formatFrequency(14_000)).isEqualTo("14 kHz")
        assertThat(formatFrequency(176_400)).isEqualTo("176.4 kHz")
    }
}
