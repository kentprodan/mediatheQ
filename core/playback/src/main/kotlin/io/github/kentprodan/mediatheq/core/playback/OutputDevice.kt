package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioDeviceInfo
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind
import java.security.MessageDigest

/** The device media plays on (#140), as the platform names it: [address] is its own (a Bluetooth MAC, maybe anonymized). */
internal data class OutputDevice(
    val kind: OutputKind,
    val name: String = "",
    val address: String = "",
) {
    /**
     * The same for the same device on every connect, never the raw address: Bluetooth by address and name (the address
     * may come anonymized without BLUETOOTH_CONNECT, only its last bytes real); USB by product name only, since its
     * address (`card=1;device=0`) changes with the port. Two identical DACs share one profile.
     */
    val id: OutputId
        get() =
            when (kind) {
                OutputKind.Phone -> OutputId.Phone
                OutputKind.Bluetooth -> OutputId(kind, hash("$address|$name"))
                OutputKind.Usb -> OutputId(kind, hash(name))
            }

    companion object {
        val Phone = OutputDevice(OutputKind.Phone)

        private fun hash(value: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray())
                .take(HASH_BYTES)
                .joinToString("") { "%02x".format(it) }

        private const val HASH_BYTES = 8
    }
}

/** The kind of output an [AudioDeviceInfo] type is: built-in speakers, wired and anything else count as the phone. */
internal fun outputKind(type: Int): OutputKind =
    when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_HEARING_AID,
        -> OutputKind.Bluetooth

        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        -> OutputKind.Usb

        else -> OutputKind.Phone
    }
