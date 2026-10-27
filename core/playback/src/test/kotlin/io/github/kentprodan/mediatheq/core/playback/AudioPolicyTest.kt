package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import org.junit.Test

class AudioPolicyTest {
    private val bitPerfect = AudioSettings(bitPerfectUsb = true)

    @Test
    fun `nothing needs the samples, so audio is offloaded`() {
        assertThat(AudioPolicy.decide(AudioSettings(), AudioRoute.Other, bitPerfectFormat = false))
            .isEqualTo(AudioMode.Offload)
        assertThat(AudioPolicy.offload(AudioSettings(), AudioRoute.Other)).isTrue()
    }

    @Test
    fun `the equalizer needs the samples, so audio is processed and not offloaded`() {
        val eq = AudioSettings(equalizerOn = true)

        assertThat(AudioPolicy.decide(eq, AudioRoute.Other, bitPerfectFormat = false)).isEqualTo(AudioMode.Processed)
        assertThat(AudioPolicy.offload(eq, AudioRoute.Other)).isFalse()
    }

    @Test
    fun `a DAC with the setting on and a matching format plays bit-perfect`() {
        assertThat(AudioPolicy.decide(bitPerfect, AudioRoute.UsbDac, bitPerfectFormat = true))
            .isEqualTo(AudioMode.BitPerfect)
    }

    @Test
    fun `bit-perfect wins over the equalizer`() {
        val both = bitPerfect.copy(equalizerOn = true)

        assertThat(AudioPolicy.decide(both, AudioRoute.UsbDac, bitPerfectFormat = true)).isEqualTo(AudioMode.BitPerfect)
    }

    @Test
    fun `a DAC with the setting on is never offloaded, so the bit-perfect mixer can be reached`() {
        assertThat(AudioPolicy.offload(bitPerfect, AudioRoute.UsbDac)).isFalse()
    }

    @Test
    fun `without a matching format the track plays normally, with the equalizer`() {
        val both = bitPerfect.copy(equalizerOn = true)

        assertThat(AudioPolicy.decide(bitPerfect, AudioRoute.UsbDac, bitPerfectFormat = false))
            .isEqualTo(AudioMode.Offload)
        assertThat(AudioPolicy.decide(both, AudioRoute.UsbDac, bitPerfectFormat = false))
            .isEqualTo(AudioMode.Processed)
    }

    @Test
    fun `the bit-perfect setting is ignored without a DAC`() {
        assertThat(AudioPolicy.wantsBitPerfect(bitPerfect, AudioRoute.Other)).isFalse()
        assertThat(AudioPolicy.decide(bitPerfect, AudioRoute.Other, bitPerfectFormat = true))
            .isEqualTo(AudioMode.Offload)
        assertThat(AudioPolicy.offload(bitPerfect, AudioRoute.Other)).isTrue()
    }

    @Test
    fun `a DAC with the setting off plays like any output`() {
        assertThat(AudioPolicy.wantsBitPerfect(AudioSettings(), AudioRoute.UsbDac)).isFalse()
        assertThat(AudioPolicy.decide(AudioSettings(), AudioRoute.UsbDac, bitPerfectFormat = true))
            .isEqualTo(AudioMode.Offload)
        assertThat(AudioPolicy.offload(AudioSettings(), AudioRoute.UsbDac)).isTrue()
    }

    @Test
    fun `bit-perfect ignores normalization`() {
        assertThat(AudioPolicy.volume(ItemGains(-6.0, -6.0), Normalization.Album, AudioMode.BitPerfect)).isEqualTo(1f)
    }

    @Test
    fun `processed audio is still normalized`() {
        assertThat(AudioPolicy.volume(ItemGains(-6.0, null), Normalization.Album, AudioMode.Processed))
            .isWithin(0.001f)
            .of(0.501f)
    }

    @Test
    fun `album normalization uses the album gain`() {
        assertThat(
            AudioPolicy.volume(ItemGains(albumDb = -6.0, trackDb = -12.0), Normalization.Album, AudioMode.Offload),
        ).isWithin(0.001f)
            .of(0.501f)
    }

    @Test
    fun `album normalization falls back to the track gain`() {
        assertThat(
            AudioPolicy.volume(ItemGains(albumDb = null, trackDb = -20.0), Normalization.Album, AudioMode.Offload),
        ).isWithin(0.001f)
            .of(0.1f)
    }

    @Test
    fun `track normalization prefers the track gain, then the album gain`() {
        assertThat(AudioPolicy.volume(ItemGains(-6.0, -20.0), Normalization.Track, AudioMode.Offload))
            .isWithin(0.001f)
            .of(0.1f)
        assertThat(AudioPolicy.volume(ItemGains(-20.0, null), Normalization.Track, AudioMode.Offload))
            .isWithin(0.001f)
            .of(0.1f)
    }

    @Test
    fun `no gain plays at full volume`() {
        assertThat(AudioPolicy.volume(ItemGains(null, null), Normalization.Album, AudioMode.Offload)).isEqualTo(1f)
    }

    @Test
    fun `a positive gain never boosts above full volume`() {
        assertThat(AudioPolicy.volume(ItemGains(albumDb = 4.0, trackDb = null), Normalization.Album, AudioMode.Offload))
            .isEqualTo(1f)
    }

    @Test
    fun `normalization off ignores the gains`() {
        assertThat(AudioPolicy.volume(ItemGains(-6.0, -6.0), Normalization.Off, AudioMode.Offload)).isEqualTo(1f)
    }

    @Test
    fun `an Atmos track the server converts is a converted Atmos track`() {
        assertThat(AudioPolicy.atmosConverted(isAtmos = true, PlayMethod.Transcode, convertedDownload = false)).isTrue()
        assertThat(
            AudioPolicy.atmosConverted(isAtmos = true, PlayMethod.DirectStream, convertedDownload = false),
        ).isTrue()
    }

    @Test
    fun `an Atmos track played as it is (a JOC decoder) is not converted`() {
        assertThat(
            AudioPolicy.atmosConverted(isAtmos = true, PlayMethod.DirectPlay, convertedDownload = false),
        ).isFalse()
    }

    @Test
    fun `an Atmos track whose stream is not resolved yet says nothing`() {
        assertThat(AudioPolicy.atmosConverted(isAtmos = true, playMethod = null, convertedDownload = false)).isFalse()
    }

    @Test
    fun `an Atmos track played from a converted download is a converted Atmos track`() {
        assertThat(AudioPolicy.atmosConverted(isAtmos = true, PlayMethod.DirectPlay, convertedDownload = true)).isTrue()
    }

    @Test
    fun `a converted download is not a downgrade before its stream is known`() {
        assertThat(AudioPolicy.atmosConverted(isAtmos = true, playMethod = null, convertedDownload = true)).isFalse()
    }

    @Test
    fun `a converted track without Atmos is not an Atmos downgrade`() {
        assertThat(
            AudioPolicy.atmosConverted(isAtmos = false, PlayMethod.Transcode, convertedDownload = false),
        ).isFalse()
    }
}
