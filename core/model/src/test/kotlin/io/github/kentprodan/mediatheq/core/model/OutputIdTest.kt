package io.github.kentprodan.mediatheq.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OutputIdTest {
    @Test
    fun `ids read back from their string form`() {
        val bluetooth = OutputId(OutputKind.Bluetooth, "a1b2")
        val usb = OutputId(OutputKind.Usb, "c3d4")

        assertThat(OutputId.Phone.toString()).isEqualTo("phone")
        assertThat(bluetooth.toString()).isEqualTo("bluetooth:a1b2")
        assertThat(OutputId.parseOrNull("phone")).isEqualTo(OutputId.Phone)
        assertThat(OutputId.parseOrNull(bluetooth.toString())).isEqualTo(bluetooth)
        assertThat(OutputId.parseOrNull(usb.toString())).isEqualTo(usb)
    }

    @Test
    fun `damaged or unknown values read as nothing`() {
        listOf("", "phone:x", "bluetooth:", "cast:1", ":x", "usb:a:b", "bluetooth").forEach {
            assertThat(OutputId.parseOrNull(it)).isNull()
        }
    }

    @Test
    fun `an HDMI output reads back`() {
        assertThat(OutputId.parseOrNull("hdmi:abc")).isEqualTo(OutputId(OutputKind.Hdmi, "abc"))
    }
}
