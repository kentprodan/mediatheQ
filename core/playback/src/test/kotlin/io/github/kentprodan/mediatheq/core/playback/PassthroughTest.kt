package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioDeviceInfo
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind
import org.junit.Test

/** Dolby Atmos passthrough over HDMI (ADR 0039, #241): the policy's rules and the controller's decisions. */
class PassthroughTest {
    private val joc = MimeTypes.AUDIO_E_AC3_JOC
    private val set = setOf(joc, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_TRUEHD)
    private val on = AudioSettings()
    private val off = AudioSettings(atmosPassthrough = false)
    private val gains = ItemGains(albumDb = -6.0, trackDb = null)

    @Test
    fun `HDMI with the switch on and the track's codec in the set passes through`() {
        assertThat(AudioPolicy.decide(on, AudioRoute.Hdmi, false, set, joc)).isEqualTo(AudioMode.Passthrough)
    }

    @Test
    fun `the switch off, a codec not in the set, or no HDMI plays as before`() {
        assertThat(AudioPolicy.decide(off, AudioRoute.Hdmi, false, set, joc)).isEqualTo(AudioMode.Offload)
        assertThat(
            AudioPolicy.decide(on, AudioRoute.Hdmi, false, set, MimeTypes.AUDIO_FLAC),
        ).isEqualTo(AudioMode.Offload)
        assertThat(AudioPolicy.decide(on, AudioRoute.Hdmi, false, set, null)).isEqualTo(AudioMode.Offload)
        assertThat(AudioPolicy.decide(on, AudioRoute.Other, false, set, joc)).isEqualTo(AudioMode.Offload)
        // The equalizer still processes what does not pass through.
        assertThat(AudioPolicy.decide(on.copy(equalizerOn = true), AudioRoute.Hdmi, false, set, null))
            .isEqualTo(AudioMode.Processed)
    }

    @Test
    fun `passthrough wins over the equalizer, a USB DAC is unaffected`() {
        assertThat(AudioPolicy.decide(on.copy(equalizerOn = true), AudioRoute.Hdmi, false, set, joc))
            .isEqualTo(AudioMode.Passthrough)
        val dac = on.copy(bitPerfectUsb = true)
        assertThat(AudioPolicy.decide(dac, AudioRoute.UsbDac, true, set, joc)).isEqualTo(AudioMode.BitPerfect)
        assertThat(AudioPolicy.wantsPassthrough(on, AudioRoute.UsbDac, set)).isFalse()
    }

    @Test
    fun `offload is off on an HDMI route that passes something through, per route`() {
        assertThat(AudioPolicy.offload(on, AudioRoute.Hdmi, set)).isFalse()
        assertThat(AudioPolicy.offload(on, AudioRoute.Hdmi, emptySet())).isTrue()
        assertThat(AudioPolicy.offload(off, AudioRoute.Hdmi, set)).isTrue()
        assertThat(AudioPolicy.offload(on, AudioRoute.Other, set)).isTrue()
    }

    @Test
    fun `passthrough plays at volume 1, without normalization, and fades skip it`() {
        assertThat(AudioPolicy.volume(gains, Normalization.Album, AudioMode.Passthrough)).isEqualTo(1f)
        assertThat(AudioPolicy.volume(gains, Normalization.Album, AudioMode.Offload)).isLessThan(1f)
        assertThat(AudioPolicy.fixedVolume(AudioMode.Passthrough)).isTrue()
        assertThat(AudioPolicy.fixedVolume(AudioMode.Processed)).isFalse()
    }

    @Test
    fun `a file's codec maps to its passthrough type, JOC only with Atmos`() {
        assertThat(passthroughMime("eac3", isAtmos = true)).isEqualTo(MimeTypes.AUDIO_E_AC3_JOC)
        assertThat(passthroughMime("EAC3", isAtmos = false)).isEqualTo(MimeTypes.AUDIO_E_AC3)
        assertThat(passthroughMime("ac3", isAtmos = false)).isEqualTo(MimeTypes.AUDIO_AC3)
        assertThat(passthroughMime("truehd", isAtmos = true)).isEqualTo(MimeTypes.AUDIO_TRUEHD)
        assertThat(passthroughMime("flac", isAtmos = false)).isNull()
        assertThat(passthroughMime(null, isAtmos = true)).isNull()
    }

    @Test
    fun `the capabilities become MIME types, the opened encoding tells Atmos`() {
        val supported = setOf(C.ENCODING_E_AC3_JOC, C.ENCODING_E_AC3, C.ENCODING_PCM_16BIT)
        assertThat(
            passthroughMimes { it in supported },
        ).containsExactly(MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_E_AC3)
        assertThat(passthroughMimes { false }).isEmpty()
        assertThat(atmosPassthrough(C.ENCODING_E_AC3_JOC, isAtmosFile = true)).isTrue()
        assertThat(atmosPassthrough(C.ENCODING_DOLBY_TRUEHD, isAtmosFile = true)).isTrue()
        // Plain 7.1 TrueHD is not Atmos; E-AC-3 without JOC or PCM is not either.
        assertThat(atmosPassthrough(C.ENCODING_DOLBY_TRUEHD, isAtmosFile = false)).isFalse()
        assertThat(atmosPassthrough(C.ENCODING_E_AC3, isAtmosFile = true)).isFalse()
        assertThat(atmosPassthrough(null, isAtmosFile = true)).isFalse()
    }

    @Test
    fun `the three HDMI types are an HDMI output, remembered by its name`() {
        listOf(AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC).forEach {
            assertThat(outputKind(it)).isEqualTo(OutputKind.Hdmi)
        }
        val tv = OutputDevice(OutputKind.Hdmi, "LG TV", "port 1")
        assertThat(tv.id).isEqualTo(OutputDevice(OutputKind.Hdmi, "LG TV", "port 2").id)
        assertThat(OutputId.parseOrNull(tv.id.toString())).isEqualTo(tv.id)
        assertThat(tv.id.toString()).startsWith("hdmi:")
    }

    // The controller on an HDMI output.

    private val port = FakeUsbAudioPort().apply { output = OutputDevice(OutputKind.Hdmi, "LG TV") }
    private var capabilities = set
    private var mime: String? = joc
    private var played = PlayedStream(itemId = "item 1")
    private val controller =
        AudioOutputController(
            port,
            { null },
            {},
            { false },
            played = { played },
            passthroughCapabilities = { capabilities },
            currentMime = { mime },
        )

    @Test
    fun `on HDMI a JOC file passes through, a stereo one does not, and offload is off`() {
        controller.setSettings(on)
        assertThat(controller.state.route).isEqualTo(AudioRoute.Hdmi)
        assertThat(controller.state.mode).isEqualTo(AudioMode.Passthrough)
        assertThat(controller.state.offload).isFalse()

        mime = null
        controller.refresh("transition")
        assertThat(controller.state.mode).isEqualTo(AudioMode.Offload)
    }

    @Test
    fun `turning the HDMI output's switch off plays processed or offloaded again`() {
        controller.setSettings(on)
        controller.setSettings(off)

        assertThat(controller.state.mode).isEqualTo(AudioMode.Offload)
        assertThat(controller.state.offload).isTrue()
    }

    @Test
    fun `stream requests ask to pass through only on HDMI with the output's switch on`() {
        controller.setSettings(on)
        assertThat(controller.state.requestPassthrough).isEqualTo(set)

        controller.setSettings(off)
        assertThat(controller.state.requestPassthrough).isEmpty()

        port.output = OutputDevice.Phone
        controller.setSettings(on)
        assertThat(controller.state.requestPassthrough).isEmpty()
    }

    @Test
    fun `a capabilities change decides again`() {
        capabilities = emptySet()
        controller.setSettings(on)
        assertThat(controller.state.mode).isEqualTo(AudioMode.Offload)

        capabilities = set
        controller.onCapabilitiesChanged()

        assertThat(controller.state.mode).isEqualTo(AudioMode.Passthrough)
        assertThat(controller.state.passthrough).isEqualTo(set)
    }

    @Test
    fun `the opened track's encoding is kept while it is open, an older track's release does not drop it`() {
        controller.setSettings(on)
        controller.onAudioTrackOpened(48_000, C.ENCODING_PCM_16BIT)
        controller.onAudioTrackOpened(48_000, C.ENCODING_E_AC3_JOC)
        controller.onAudioTrackReleased()
        assertThat(controller.state.passthroughEncoding).isEqualTo(C.ENCODING_E_AC3_JOC)

        controller.onAudioTrackReleased()
        assertThat(controller.state.passthroughEncoding).isNull()
    }

    @Test
    fun `a failed passthrough track is prepared again once, then the error shows`() {
        controller.setSettings(on)
        controller.refresh("ready")

        assertThat(controller.onPassthroughFailed()).isTrue()
        assertThat(controller.onPassthroughFailed()).isFalse()

        // The next item gets its own retry; outside passthrough the error is not ours.
        played = PlayedStream(itemId = "item 2")
        controller.refresh("transition")
        assertThat(controller.onPassthroughFailed()).isTrue()
        mime = null
        controller.refresh("transition")
        assertThat(controller.onPassthroughFailed()).isFalse()
    }

    @Test
    fun `JOC passes through as E-AC-3 where only that is supported`() {
        val eac3Only = setOf(MimeTypes.AUDIO_E_AC3)
        assertThat(AudioPolicy.decide(on, AudioRoute.Hdmi, false, eac3Only, joc)).isEqualTo(AudioMode.Passthrough)
        assertThat(AudioPolicy.decide(on, AudioRoute.Hdmi, false, eac3Only, MimeTypes.AUDIO_TRUEHD))
            .isEqualTo(AudioMode.Offload)
    }

    @Test
    fun `a passthrough track failing after the route already changed is still retried once`() {
        controller.setSettings(on)
        controller.onAudioTrackOpened(48_000, C.ENCODING_E_AC3_JOC)
        // HDMI unplugged: decided before the write error arrives.
        port.output = OutputDevice.Phone
        controller.refresh("device")
        assertThat(controller.state.mode).isEqualTo(AudioMode.Offload)

        assertThat(controller.onPassthroughFailed()).isTrue()
        assertThat(controller.onPassthroughFailed()).isFalse()
    }

    @Test
    fun `a PCM track failing is not ours to retry, and a retried item that opens again gets a retry later`() {
        controller.setSettings(on.copy(atmosPassthrough = false))
        controller.onAudioTrackOpened(48_000, C.ENCODING_PCM_16BIT)
        assertThat(controller.onPassthroughFailed()).isFalse()

        controller.setSettings(on)
        controller.onAudioTrackOpened(48_000, C.ENCODING_E_AC3_JOC)
        assertThat(controller.onPassthroughFailed()).isTrue()
        controller.onAudioTrackOpened(48_000, C.ENCODING_E_AC3_JOC)
        assertThat(controller.onPassthroughFailed()).isTrue()
    }
}
