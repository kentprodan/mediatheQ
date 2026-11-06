package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioDeviceInfo
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind
import org.junit.Test

class OutputDeviceTest {
    private val m4 = OutputDevice(OutputKind.Bluetooth, "MOMENTUM 4", "00:1B:66:AA:BB:CC")

    @Test
    fun `the same Bluetooth device has the same id on every connect, without its address in it`() {
        assertThat(m4.copy().id).isEqualTo(m4.id)
        assertThat(m4.id.kind).isEqualTo(OutputKind.Bluetooth)
        assertThat(m4.id.toString()).doesNotContain("00:1B")
        assertThat(m4.copy(address = "00:1B:66:AA:BB:CD").id).isNotEqualTo(m4.id)
        assertThat(m4.copy(name = "Car").id).isNotEqualTo(m4.id)
    }

    @Test
    fun `a USB DAC is known by its name, whatever port it is on`() {
        val dac = OutputDevice(OutputKind.Usb, "Apple USB-C to 3.5mm", "card=1;device=0")

        assertThat(dac.copy(address = "card=2;device=0").id).isEqualTo(dac.id)
        assertThat(dac.id.kind).isEqualTo(OutputKind.Usb)
    }

    @Test
    fun `the phone is one output, whatever its speaker`() {
        assertThat(OutputDevice.Phone.id).isEqualTo(OutputId.Phone)
        assertThat(OutputDevice(OutputKind.Phone, "Pixel 9", "x").id).isEqualTo(OutputId.Phone)
    }

    @Test
    fun `device types map to their kind`() {
        assertThat(outputKind(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)).isEqualTo(OutputKind.Bluetooth)
        assertThat(outputKind(AudioDeviceInfo.TYPE_BLE_HEADSET)).isEqualTo(OutputKind.Bluetooth)
        assertThat(outputKind(AudioDeviceInfo.TYPE_USB_DEVICE)).isEqualTo(OutputKind.Usb)
        assertThat(outputKind(AudioDeviceInfo.TYPE_USB_HEADSET)).isEqualTo(OutputKind.Usb)
        assertThat(outputKind(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)).isEqualTo(OutputKind.Phone)
        assertThat(outputKind(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)).isEqualTo(OutputKind.Phone)
    }
}
