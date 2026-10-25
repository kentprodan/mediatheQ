package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioOutputControllerTest {
    private val cd = PcmFormat(AudioFormat.ENCODING_PCM_16BIT, 44_100, AudioFormat.CHANNEL_OUT_STEREO)
    private val hiRes = cd.copy(sampleRateHz = 96_000)
    private val port = FakeUsbAudioPort()
    private var format: PcmFormat? = cd
    private val changes = mutableListOf<AudioOutputState>()
    private var atmosConverted = false
    private val controller = AudioOutputController(port, { format }, changes::add) { atmosConverted }
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
        controller.refresh()

        assertThat(port.preferred).isEqualTo(MixerCapability(hiRes, true))
        assertThat(controller.state.bitPerfectFormat).isEqualTo(hiRes)
    }

    @Test
    fun `the same format is not set again`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.setSettings(bitPerfectOn)

        controller.refresh()

        assertThat(port.setCalls).isEqualTo(1)
    }

    @Test
    fun `unplugging the DAC clears the preference and allows offload again`() {
        port.capabilities = listOf(MixerCapability(cd, true))
        controller.setSettings(bitPerfectOn)

        port.capabilities = null
        controller.refresh()

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
        controller.refresh()

        assertThat(port.preferred).isNull()
    }

    @Test
    fun `a refused next format clears the previous one`() {
        port.capabilities = listOf(MixerCapability(cd, true), MixerCapability(hiRes, true))
        controller.setSettings(bitPerfectOn)
        port.accepts = false

        format = hiRes
        controller.refresh()

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
    fun `listeners hear only real changes`() {
        controller.refresh()
        controller.setSettings(AudioSettings(equalizerOn = true))
        controller.refresh()

        assertThat(changes).containsExactly(AudioOutputState(offload = false, mode = AudioMode.Processed))
    }

    private class FakeUsbAudioPort : UsbAudioPort {
        var capabilities: List<MixerCapability>? = null
        var accepts = true
        var preferred: MixerCapability? = null
        var setCalls = 0

        override fun dacCapabilities() = capabilities

        override fun setPreferred(capability: MixerCapability): Boolean {
            setCalls++
            if (accepts) preferred = capability
            return accepts
        }

        override fun clearPreferred() {
            preferred = null
        }

        override fun watch(onChange: () -> Unit) = Unit

        override fun release() = Unit
    }

    @Test
    fun `an Atmos track starting to play converted is published on its own`() {
        controller.refresh()
        changes.clear()

        atmosConverted = true
        controller.refresh()

        assertThat(changes).containsExactly(AudioOutputState(atmosConverted = true))
    }
}
