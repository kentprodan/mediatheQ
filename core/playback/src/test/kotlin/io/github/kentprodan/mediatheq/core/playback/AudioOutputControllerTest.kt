package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioFormat
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.OutputKind
import org.junit.Test

class AudioOutputControllerTest {
    private val cd = PcmFormat(AudioFormat.ENCODING_PCM_16BIT, 44_100, AudioFormat.CHANNEL_OUT_STEREO)
    private val hiRes = cd.copy(sampleRateHz = 96_000)
    private val port = FakeUsbAudioPort()
    private var format: PcmFormat? = cd
    private val changes = mutableListOf<AudioOutputState>()
    private var atmosConverted = false
    private var played = PlayedStream()
    private var reopens = 0
    private val controller =
        AudioOutputController(port, { format }, changes::add, { atmosConverted }, played = { played }) { reopens++ }
    private val bitPerfectOn = AudioSettings(bitPerfectUsb = true)

    @Test
    fun `without a DAC nothing is preferred and audio is offloaded`() {
        controller.setSettings(bitPerfectOn)

        assertThat(controller.state).isEqualTo(AudioOutputState())
        assertThat(port.preferred).isNull()
    }

    @Test
    fun `a DAC with a matching format plays bit-perfect`() {
        port.capabilities = listOf(MixerCapability(cd, bitPerfect = true))

        controller.setSettings(bitPerfectOn)

        assertThat(port.preferred).isEqualTo(MixerCapability(cd, bitPerfect = true))
        assertThat(controller.state)
            .isEqualTo(AudioOutputState(AudioRoute.UsbDac, offload = false, AudioMode.BitPerfect, cd))
    }

    @Test
    fun `without a matching format the track plays normally and nothing is preferred`() {
        port.capabilities = listOf(MixerCapability(hiRes, bitPerfect = true))

        controller.setSettings(bitPerfectOn.copy(equalizerOn = true))

        assertThat(port.preferred).isNull()
        assertThat(controller.state)
            .isEqualTo(AudioOutputState(AudioRoute.UsbDac, offload = false, AudioMode.Processed, null, true))
    }

    @Test
    fun `a refused preference plays normally`() {
        port.capabilities = listOf(MixerCapability(cd, bitPerfect = true))
        port.accepts = false

        controller.setSettings(bitPerfectOn)

        assertThat(controller.state.mode).isEqualTo(AudioMode.Offload)
        assertThat(controller.state.bitPerfectFormat).isNull()
    }

    @Test
    fun `the next track at another rate gets its own mixer attribute`() {
        port.capabilities = listOf(MixerCapability(cd, true), MixerCapability(hiRes, true))
        controller.setSettings(bitPerfectOn)

        format = hiRes
        controller.refresh("test")

        assertThat(port.preferred).isEqualTo(MixerCapability(hiRes, true))
        assertThat(controller.state.bitPerfectFormat).isEqualTo(hiRes)
    }

    @Test
    fun `the same format is not set again`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.setSettings(bitPerfectOn)

        controller.refresh("test")

        assertThat(port.setCalls).isEqualTo(1)
    }

    @Test
    fun `unplugging the DAC clears the preference and allows offload again`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.setSettings(bitPerfectOn)

        port.capabilities = null
        controller.refresh("test")

        assertThat(port.preferred).isNull()
        assertThat(controller.state).isEqualTo(AudioOutputState())
    }

    @Test
    fun `turning the setting off clears the preference`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.setSettings(bitPerfectOn)

        controller.setSettings(AudioSettings())

        assertThat(port.preferred).isNull()
        assertThat(controller.state).isEqualTo(AudioOutputState(AudioRoute.UsbDac, offload = true, AudioMode.Offload))
    }

    @Test
    fun `stopping playback clears the preference`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.setSettings(bitPerfectOn)

        format = null
        controller.refresh("test")

        assertThat(port.preferred).isNull()
    }

    @Test
    fun `a refused next format clears the previous one`() {
        port.capabilities = listOf(MixerCapability(cd, true), MixerCapability(hiRes, true))
        controller.setSettings(bitPerfectOn)
        port.accepts = false

        format = hiRes
        controller.refresh("test")

        assertThat(port.preferred).isNull()
        assertThat(controller.state.mode).isEqualTo(AudioMode.Offload)
        assertThat(controller.state.bitPerfectNoMatch).isTrue()
    }

    @Test
    fun `stopped or unknown, nothing was tried, so it is no mismatch`() {
        port.capabilities = listOf(MixerCapability(hiRes, true))
        format = null

        controller.setSettings(bitPerfectOn)

        assertThat(controller.state.bitPerfectNoMatch).isFalse()
        assertThat(controller.state.route).isEqualTo(AudioRoute.UsbDac)
    }

    @Test
    fun `release clears the preference`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.setSettings(bitPerfectOn)

        controller.release()

        assertThat(port.preferred).isNull()
    }

    @Test
    fun `turning the setting on with an AudioTrack open asks once for a new one`() {
        port.capabilities = listOf(MixerCapability(cd, bitPerfect = true))
        controller.onAudioTrackOpened(44_100)

        controller.setSettings(bitPerfectOn)
        controller.refresh("state")
        controller.setSettings(bitPerfectOn.copy(equalizerOn = true))

        assertThat(reopens).isEqualTo(1)
    }

    @Test
    fun `turning the setting on with no AudioTrack open asks for nothing, the next one opens bit-perfect`() {
        port.capabilities = listOf(MixerCapability(cd, bitPerfect = true))

        controller.setSettings(bitPerfectOn)

        assertThat(reopens).isEqualTo(0)
    }

    @Test
    fun `a transition to another rate with the old track still open asks for nothing`() {
        port.capabilities = listOf(MixerCapability(cd, bitPerfect = true), MixerCapability(hiRes, bitPerfect = true))
        controller.setSettings(bitPerfectOn)
        controller.onAudioTrackOpened(44_100)

        format = hiRes
        controller.refresh("transition")

        assertThat(port.preferred).isEqualTo(MixerCapability(hiRes, bitPerfect = true))
        assertThat(reopens).isEqualTo(0)
    }

    @Test
    fun `no matching attribute, a refused one or the setting turned off asks for nothing`() {
        controller.onAudioTrackOpened(44_100)
        port.capabilities = listOf(MixerCapability(hiRes, bitPerfect = true))
        controller.setSettings(bitPerfectOn)
        controller.setSettings(AudioSettings())

        port.capabilities = listOf(MixerCapability(cd, bitPerfect = true))
        port.accepts = false
        controller.setSettings(bitPerfectOn)

        assertThat(reopens).isEqualTo(0)
    }

    @Test
    fun `a preference set again after the platform dropped it reopens the open track`() {
        port.capabilities = listOf(MixerCapability(cd, bitPerfect = true))
        controller.setSettings(bitPerfectOn)
        controller.onAudioTrackOpened(44_100)

        controller.onPreferenceDropped()

        assertThat(reopens).isEqualTo(1)
    }

    @Test
    fun `listeners hear only real changes`() {
        controller.refresh("test")
        controller.setSettings(AudioSettings(equalizerOn = true))
        controller.refresh("test")

        assertThat(changes).containsExactly(AudioOutputState(offload = false, mode = AudioMode.Processed))
    }

    @Test
    fun `an Atmos track starting to play converted is published on its own`() {
        controller.refresh("test")
        changes.clear()

        atmosConverted = true
        controller.refresh("test")

        assertThat(changes).containsExactly(AudioOutputState(atmosConverted = true))
    }

    @Test
    fun `a new AudioTrack opened after the preference is confirmed bit-perfect, also after a rate change`() {
        port.capabilities = listOf(MixerCapability(cd, true), MixerCapability(hiRes, true))
        controller.setSettings(bitPerfectOn)
        assertThat(controller.state.mode).isEqualTo(AudioMode.BitPerfect)
        assertThat(controller.state.bitPerfectConfirmed).isFalse()

        controller.onAudioTrackOpened(44_100)
        assertThat(controller.state.bitPerfectConfirmed).isTrue()

        // A rate change: the old AudioTrack plays out and is released, then the new rate opens its own.
        format = hiRes
        controller.refresh("transition")
        assertThat(controller.state.bitPerfectConfirmed).isFalse()
        controller.onAudioTrackReleased()
        controller.onAudioTrackOpened(96_000)
        assertThat(controller.state.bitPerfectConfirmed).isTrue()
    }

    @Test
    fun `an AudioTrack already open when the setting is turned on is not claimed bit-perfect`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.onAudioTrackOpened(44_100)

        controller.setSettings(bitPerfectOn)

        assertThat(controller.state.mode).isEqualTo(AudioMode.BitPerfect)
        assertThat(controller.state.bitPerfectConfirmed).isFalse()
    }

    @Test
    fun `reopening without releasing first is not claimed bit-perfect either`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.onAudioTrackOpened(44_100)
        controller.setSettings(bitPerfectOn)

        controller.onAudioTrackOpened(44_100)

        assertThat(controller.state.bitPerfectConfirmed).isFalse()
    }

    @Test
    fun `without a preference an opened AudioTrack confirms nothing`() {
        port.capabilities = listOf(MixerCapability(hiRes, true))
        controller.setSettings(bitPerfectOn)

        controller.onAudioTrackOpened(44_100)

        assertThat(controller.state.bitPerfectConfirmed).isFalse()
    }

    @Test
    fun `a released AudioTrack is no longer bit-perfect`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.setSettings(bitPerfectOn)
        controller.onAudioTrackOpened(44_100)

        controller.onAudioTrackReleased()

        assertThat(controller.state.bitPerfectConfirmed).isFalse()
    }

    @Test
    fun `a preference the platform dropped is set again`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.setSettings(bitPerfectOn)
        port.preferred = null

        controller.onPreferenceDropped()

        assertThat(port.preferred).isEqualTo(MixerCapability(cd, true))
        assertThat(port.setCalls).isEqualTo(2)
    }

    @Test
    fun `a dropped preference after the setting went off is not set again`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.setSettings(AudioSettings())

        controller.onPreferenceDropped()

        assertThat(port.setCalls).isEqualTo(0)
    }

    @Test
    fun `when bit-perfect fails the preference goes and playback is normal until the device changes`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.setSettings(bitPerfectOn)

        assertThat(controller.onBitPerfectFailed()).isTrue()

        assertThat(port.preferred).isNull()
        assertThat(controller.state.mode).isEqualTo(AudioMode.Offload)
        controller.refresh("transition")
        assertThat(port.preferred).isNull()

        controller.onDeviceChanged()
        assertThat(port.preferred).isEqualTo(MixerCapability(cd, true))
    }

    @Test
    fun `turning the setting off and on again tries bit-perfect again after a failure`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.setSettings(bitPerfectOn)
        controller.onBitPerfectFailed()

        controller.setSettings(AudioSettings())
        controller.setSettings(bitPerfectOn)

        assertThat(port.preferred).isEqualTo(MixerCapability(cd, true))
    }

    @Test
    fun `an error without a preference is not bit-perfect's to handle`() {
        port.capabilities = listOf(MixerCapability(cd, true))

        assertThat(controller.onBitPerfectFailed()).isFalse()
    }

    @Test
    fun `an old AudioTrack released after the next one opened does not make the next open look like the first`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.onAudioTrackOpened(44_100)
        controller.setSettings(bitPerfectOn)

        // Seeks: each opens a new AudioTrack before the old one's background release finishes.
        controller.onAudioTrackOpened(44_100)
        controller.onAudioTrackReleased()
        controller.onAudioTrackOpened(44_100)

        assertThat(controller.state.bitPerfectConfirmed).isFalse()
    }

    @Test
    fun `a stream resolved after the transition is published at the next refresh`() {
        controller.refresh("transition")
        changes.clear()

        played = PlayedStream(known = true, convertedKbps = 256)
        controller.refresh("ready")

        assertThat(changes).containsExactly(AudioOutputState(played = PlayedStream(known = true, convertedKbps = 256)))
    }

    @Test
    fun `the device media plays on is published with each decision`() {
        val headphones = OutputDevice(OutputKind.Bluetooth, "MOMENTUM 4", "00:11:22:33:44:55")
        controller.refresh("test")

        port.output = headphones
        controller.onDeviceChanged()

        assertThat(controller.state.output).isEqualTo(headphones)
    }
}
