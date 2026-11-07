package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioFormat
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.data.OutputProfile
import io.github.kentprodan.mediatheq.core.data.RememberedOutput
import io.github.kentprodan.mediatheq.core.data.RememberedOutputs
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind
import org.junit.Test

class OutputProfileFollowerTest {
    private val headphones = OutputDevice(OutputKind.Bluetooth, "MOMENTUM 4", "00:1B:66:AA:BB:CC")
    private val car = OutputDevice(OutputKind.Bluetooth, "VW", "00:1B:66:00:00:01")
    private val dac = OutputDevice(OutputKind.Usb, "DAC")
    private val phoneEq = OutputProfile(equalizerOn = true, eqPreset = EqPreset.BassBoost)
    private val outputs =
        RememberedOutputs(
            listOf(
                RememberedOutput(OutputId.Phone, "", phoneEq),
                RememberedOutput(headphones.id, headphones.name, OutputProfile(normalization = Normalization.Track)),
                RememberedOutput(car.id, car.name, OutputProfile(equalizerOn = true, bypass = true)),
                RememberedOutput(dac.id, dac.name, OutputProfile(bitPerfectUsb = true)),
            ),
        )

    private val remembered = mutableListOf<OutputDevice>()
    private val applied = mutableListOf<OutputProfile>()
    private val follower = OutputProfileFollower(remembered::add, applied::add)

    @Test
    fun `each device gets its own profile, an unknown one the phone's, and every device is remembered`() {
        follower.onOutputs(outputs)
        val stranger = OutputDevice(OutputKind.Bluetooth, "Speaker", "00:00:00:00:00:09")

        follower.onDevice(headphones)
        follower.onDevice(stranger)

        assertThat(applied.last()).isEqualTo(phoneEq)
        assertThat(applied)
            .containsExactly(phoneEq, OutputProfile(normalization = Normalization.Track), phoneEq)
            .inOrder()
        assertThat(remembered).containsExactly(headphones, stranger).inOrder()
    }

    @Test
    fun `the same device again changes nothing, a changed profile applies at once`() {
        follower.onOutputs(outputs)
        follower.onDevice(headphones)
        applied.clear()

        follower.onDevice(headphones)
        assertThat(applied).isEmpty()

        val louder = OutputProfile(normalization = Normalization.Off)
        follower.onOutputs(
            outputs.copy(
                outputs =
                    outputs.outputs.map {
                        if (it.id ==
                            headphones.id
                        ) {
                            it.copy(profile = louder)
                        } else {
                            it
                        }
                    },
            ),
        )
        assertThat(applied).containsExactly(louder)
        assertThat(remembered).containsExactly(headphones)
    }

    @Test
    fun `a route change reaches the output decision with the new output's profile`() {
        val port = FakePort()
        lateinit var controller: AudioOutputController
        val follower = OutputProfileFollower({}) { controller.setSettings(it.toAudioSettings()) }
        controller =
            AudioOutputController(
                port,
                currentFormat = { PcmFormat(AudioFormat.ENCODING_PCM_16BIT, 44_100, AudioFormat.CHANNEL_OUT_STEREO) },
                onChange = { follower.onDevice(it.output) },
                atmosConverted = { false },
            )
        follower.onOutputs(outputs)
        controller.refresh("start")
        assertThat(controller.state.mode).isEqualTo(AudioMode.Processed)

        port.output = car
        controller.onDeviceChanged()
        assertThat(controller.state.mode).isEqualTo(AudioMode.Offload)
        assertThat(controller.state.offload).isTrue()

        port.output = headphones
        controller.onDeviceChanged()
        assertThat(controller.state.mode).isEqualTo(AudioMode.Offload)

        port.output = OutputDevice.Phone
        controller.onDeviceChanged()
        assertThat(controller.state.mode).isEqualTo(AudioMode.Processed)
        assertThat(controller.state.offload).isFalse()
    }

    @Test
    fun `a DAC whose profile wants bit-perfect plays bit-perfect, and back on the phone the preference is cleared`() {
        val cd = PcmFormat(AudioFormat.ENCODING_PCM_16BIT, 44_100, AudioFormat.CHANNEL_OUT_STEREO)
        val port = FakePort()
        lateinit var controller: AudioOutputController
        val follower = OutputProfileFollower({}) { controller.setSettings(it.toAudioSettings()) }
        controller = AudioOutputController(port, { cd }, { follower.onDevice(it.output) }, { false })
        follower.onOutputs(outputs)
        controller.refresh("start")

        port.output = dac
        port.capabilities = listOf(MixerCapability(cd, bitPerfect = true))
        controller.onDeviceChanged()
        assertThat(controller.state.mode).isEqualTo(AudioMode.BitPerfect)
        assertThat(port.preferred).isEqualTo(MixerCapability(cd, bitPerfect = true))

        port.output = OutputDevice.Phone
        port.capabilities = null
        controller.onDeviceChanged()
        assertThat(controller.state.mode).isEqualTo(AudioMode.Processed)
        assertThat(port.preferred).isNull()
    }

    private class FakePort : AudioOutputPort {
        var output = OutputDevice.Phone
        var capabilities: List<MixerCapability>? = null
        var preferred: MixerCapability? = null

        override fun currentOutput() = output

        override fun dacCapabilities(): List<MixerCapability>? = capabilities

        override fun setPreferred(capability: MixerCapability): Boolean {
            preferred = capability
            return true
        }

        override fun clearPreferred() {
            preferred = null
        }

        override fun watch(
            onChange: () -> Unit,
            onPreferenceDropped: () -> Unit,
        ) = Unit

        override fun release() = Unit
    }
}
