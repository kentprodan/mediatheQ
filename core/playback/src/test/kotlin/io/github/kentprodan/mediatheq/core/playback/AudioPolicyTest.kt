package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
    fun `a converted download is not bit-perfect capable`() {
        assertThat(AudioPolicy.playsOriginal(PlayMethod.DirectPlay, convertedDownload = true)).isFalse()
    }

    @Test
    fun `a direct play of the file, streamed, cached or an original download, plays the original`() {
        assertThat(AudioPolicy.playsOriginal(PlayMethod.DirectPlay, convertedDownload = false)).isTrue()
    }

    @Test
    fun `a server conversion or an unknown stream does not play the original`() {
        assertThat(AudioPolicy.playsOriginal(PlayMethod.Transcode, convertedDownload = false)).isFalse()
        assertThat(AudioPolicy.playsOriginal(PlayMethod.DirectStream, convertedDownload = false)).isFalse()
        assertThat(AudioPolicy.playsOriginal(playMethod = null, convertedDownload = false)).isFalse()
    }

    @Test
    fun `a PCM AudioTrack is a decoder, a bitstream or a passthrough decision is not`() {
        assertThat(AudioPolicy.decoderUsed(C.ENCODING_PCM_16BIT, AudioMode.Offload)).isTrue()
        assertThat(AudioPolicy.decoderUsed(C.ENCODING_E_AC3_JOC, AudioMode.Passthrough)).isFalse()
        assertThat(AudioPolicy.decoderUsed(C.ENCODING_E_AC3, AudioMode.Offload)).isFalse()
        assertThat(AudioPolicy.decoderUsed(null, AudioMode.Offload)).isFalse()
        // A FLAC's PCM track still open when the next, JOC track is decided to pass through.
        assertThat(AudioPolicy.decoderUsed(C.ENCODING_PCM_16BIT, AudioMode.Passthrough)).isFalse()
    }

    @Test
    fun `atmosDecoded and atmosConverted truth table`() {
        data class Row(
            val isAtmos: Boolean,
            val method: PlayMethod?,
            val convertedDownload: Boolean,
            val passthrough: Boolean,
            val decoder: Boolean,
            val decoded: Boolean,
            val converted: Boolean,
        )
        val rows =
            listOf(
                // A JOC file decoded on a route without passthrough (Bluetooth on the Streamer).
                Row(true, PlayMethod.DirectPlay, false, passthrough = false, decoder = true, decoded = true, false),
                // Passed through over HDMI: the badge, no note.
                Row(true, PlayMethod.DirectPlay, false, passthrough = true, decoder = false, decoded = false, false),
                // The server's stereo conversion: the stereo note only.
                Row(true, PlayMethod.Transcode, false, passthrough = false, decoder = true, decoded = false, true),
                // A converted download reports a direct play: still the stereo note only.
                Row(true, PlayMethod.DirectPlay, true, passthrough = false, decoder = true, decoded = false, true),
                // Nothing opened yet, or the stream unknown: no note.
                Row(true, PlayMethod.DirectPlay, false, passthrough = false, decoder = false, decoded = false, false),
                Row(true, null, false, passthrough = false, decoder = true, decoded = false, false),
                // JOC sent as plain E-AC-3 (the 5.1 core): a bitstream, not decoded here.
                Row(true, PlayMethod.DirectPlay, false, passthrough = false, decoder = false, decoded = false, false),
                // Not Atmos: never a note.
                Row(false, PlayMethod.DirectPlay, false, passthrough = false, decoder = true, decoded = false, false),
                Row(false, PlayMethod.Transcode, false, passthrough = false, decoder = true, decoded = false, false),
            )
        for (row in rows) {
            assertWithMessage(row.toString())
                .that(
                    AudioPolicy.atmosDecoded(
                        row.isAtmos,
                        row.method,
                        row.convertedDownload,
                        row.passthrough,
                        row.decoder,
                    ),
                ).isEqualTo(row.decoded)
            assertWithMessage(row.toString())
                .that(AudioPolicy.atmosConverted(row.isAtmos, row.method, row.convertedDownload))
                .isEqualTo(row.converted)
        }
    }

    @Test
    fun `a converted track without Atmos is not an Atmos downgrade`() {
        assertThat(
            AudioPolicy.atmosConverted(isAtmos = false, PlayMethod.Transcode, convertedDownload = false),
        ).isFalse()
    }

    @Test
    fun `a transcode plays at its quality cap, or Opus 256 for the Original quality`() {
        assertThat(
            AudioPolicy.convertedKbps(PlayMethod.Transcode, convertedDownloadKbps = null, qualityKbps = 128),
        ).isEqualTo(128)
        assertThat(
            AudioPolicy.convertedKbps(PlayMethod.Transcode, convertedDownloadKbps = null, qualityKbps = null),
        ).isEqualTo(256)
    }

    @Test
    fun `a converted download plays at the bitrate it was made with, the original file is no conversion`() {
        assertThat(
            AudioPolicy.convertedKbps(PlayMethod.DirectPlay, convertedDownloadKbps = 192, qualityKbps = null),
        ).isEqualTo(192)
        assertThat(
            AudioPolicy.convertedKbps(PlayMethod.DirectPlay, convertedDownloadKbps = null, qualityKbps = 128),
        ).isNull()
        assertThat(
            AudioPolicy.convertedKbps(PlayMethod.DirectStream, convertedDownloadKbps = null, qualityKbps = 128),
        ).isNull()
    }

    @Test
    fun `bypass turns the equalizer and normalization off, so audio is offloaded`() {
        val bypass = AudioSettings(equalizerOn = true, normalization = Normalization.Track, bypass = true)

        assertThat(AudioPolicy.decide(bypass, AudioRoute.Other, bitPerfectFormat = false)).isEqualTo(AudioMode.Offload)
        assertThat(AudioPolicy.offload(bypass, AudioRoute.Other)).isTrue()
        assertThat(AudioPolicy.normalization(bypass)).isEqualTo(Normalization.Off)
        assertThat(AudioPolicy.normalization(bypass.copy(bypass = false))).isEqualTo(Normalization.Track)
    }

    @Test
    fun `bypass leaves bit-perfect as it is`() {
        val both = bitPerfect.copy(bypass = true)

        assertThat(AudioPolicy.decide(both, AudioRoute.UsbDac, bitPerfectFormat = true)).isEqualTo(AudioMode.BitPerfect)
        assertThat(AudioPolicy.offload(both, AudioRoute.UsbDac)).isFalse()
    }

    @Test
    fun `a playing video is never offloaded`() {
        assertThat(AudioPolicy.offload(AudioSettings(), AudioRoute.Other, video = true)).isFalse()
    }
}
