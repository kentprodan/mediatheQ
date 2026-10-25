package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MixerAttributeSelectorTest {
    private val cd = PcmFormat(AudioFormat.ENCODING_PCM_16BIT, 44_100, AudioFormat.CHANNEL_OUT_STEREO)

    private fun capability(
        format: PcmFormat,
        bitPerfect: Boolean = true,
    ) = MixerCapability(format, bitPerfect)

    @Test
    fun `the local constants are the platform's values`() {
        assertThat(MixerAttributeSelector.ENCODING_PCM_16BIT).isEqualTo(AudioFormat.ENCODING_PCM_16BIT)
        assertThat(MixerAttributeSelector.CHANNEL_OUT_MONO).isEqualTo(AudioFormat.CHANNEL_OUT_MONO)
        assertThat(MixerAttributeSelector.CHANNEL_OUT_STEREO).isEqualTo(AudioFormat.CHANNEL_OUT_STEREO)
    }

    @Test
    fun `an exact bit-perfect match is chosen`() {
        val hiRes = capability(cd.copy(sampleRateHz = 96_000))
        val match = capability(cd)

        assertThat(MixerAttributeSelector.select(listOf(hiRes, match), cd)).isEqualTo(match)
    }

    @Test
    fun `a bit-perfect attribute is preferred over a default one with the same format`() {
        val mixed = capability(cd, bitPerfect = false)
        val exact = capability(cd)

        assertThat(MixerAttributeSelector.select(listOf(mixed, exact), cd)).isEqualTo(exact)
    }

    @Test
    fun `a default mixer alone is no match`() {
        assertThat(MixerAttributeSelector.select(listOf(capability(cd, bitPerfect = false)), cd)).isNull()
    }

    @Test
    fun `another sample rate is no match`() {
        assertThat(MixerAttributeSelector.select(listOf(capability(cd.copy(sampleRateHz = 48_000))), cd)).isNull()
    }

    @Test
    fun `another channel mask is no match`() {
        val mono = capability(cd.copy(channelMask = AudioFormat.CHANNEL_OUT_MONO))

        assertThat(MixerAttributeSelector.select(listOf(mono), cd)).isNull()
    }

    @Test
    fun `a DAC offering only 24-bit is no match for 16-bit output`() {
        val only24 = capability(cd.copy(encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED))

        assertThat(MixerAttributeSelector.select(listOf(only24), cd)).isNull()
    }

    @Test
    fun `no capabilities is no match`() {
        assertThat(MixerAttributeSelector.select(emptyList(), cd)).isNull()
    }

    @Test
    fun `a stereo track is sent as 16-bit stereo at its own rate`() {
        assertThat(MixerAttributeSelector.outputFormat(96_000, 2))
            .isEqualTo(PcmFormat(AudioFormat.ENCODING_PCM_16BIT, 96_000, AudioFormat.CHANNEL_OUT_STEREO))
    }

    @Test
    fun `a mono track maps to the mono mask`() {
        assertThat(MixerAttributeSelector.outputFormat(48_000, 1)?.channelMask).isEqualTo(AudioFormat.CHANNEL_OUT_MONO)
    }

    @Test
    fun `unknown rate, unknown channels or multichannel give no format`() {
        assertThat(MixerAttributeSelector.outputFormat(null, 2)).isNull()
        assertThat(MixerAttributeSelector.outputFormat(44_100, null)).isNull()
        assertThat(MixerAttributeSelector.outputFormat(48_000, 6)).isNull()
    }
}
