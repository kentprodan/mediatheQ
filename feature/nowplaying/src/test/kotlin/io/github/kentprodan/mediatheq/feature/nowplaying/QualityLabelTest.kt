package io.github.kentprodan.mediatheq.feature.nowplaying

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import org.junit.Test

class QualityLabelTest {
    private fun label(
        format: AudioFormat?,
        convertedKbps: Int? = null,
        streamKnown: Boolean = true,
    ) = qualityLabel(format, streamKnown, convertedKbps)

    private val cd = AudioFormat("flac", sampleRateHz = 44_100, bitDepth = 16, bitrateKbps = 900)

    @Test
    fun `nothing shows until the stream is known`() {
        assertThat(label(cd, streamKnown = false)).isNull()
    }

    @Test
    fun `CD quality lossless is Lossless, with its detail`() {
        assertThat(label(cd)).isEqualTo(QualityLabel(QualityKind.Lossless, "FLAC · 16/44.1 · 900 kb/s"))
    }

    @Test
    fun `more than 16 bit or more than 48 kHz is Hi-Res Lossless`() {
        assertThat(label(AudioFormat("flac", sampleRateHz = 96_000, bitDepth = 24, bitrateKbps = 1250)))
            .isEqualTo(QualityLabel(QualityKind.HiResLossless, "FLAC · 24/96 · 1250 kb/s"))
        assertThat(
            label(AudioFormat("alac", sampleRateHz = 44_100, bitDepth = 24))?.kind,
        ).isEqualTo(QualityKind.HiResLossless)
        assertThat(
            label(AudioFormat("flac", sampleRateHz = 88_200, bitDepth = 16))?.kind,
        ).isEqualTo(QualityKind.HiResLossless)
        assertThat(
            label(AudioFormat("flac", sampleRateHz = 48_000, bitDepth = 16))?.kind,
        ).isEqualTo(QualityKind.Lossless)
    }

    @Test
    fun `a lossless file without its bit depth is Lossless unless its rate says Hi-Res`() {
        assertThat(
            label(AudioFormat("flac", sampleRateHz = 44_100)),
        ).isEqualTo(QualityLabel(QualityKind.Lossless, "FLAC"))
        assertThat(label(AudioFormat("flac", sampleRateHz = 192_000))?.kind).isEqualTo(QualityKind.HiResLossless)
    }

    @Test
    fun `WAV and AIFF come as PCM sample formats and are lossless`() {
        assertThat(label(AudioFormat("pcm_s24le", sampleRateHz = 96_000, bitDepth = 24, bitrateKbps = 4608)))
            .isEqualTo(QualityLabel(QualityKind.HiResLossless, "PCM · 24/96 · 4608 kb/s"))
        assertThat(
            label(AudioFormat("pcm_s16be", sampleRateHz = 44_100, bitDepth = 16))?.kind,
        ).isEqualTo(QualityKind.Lossless)
    }

    @Test
    fun `lossy at 256 kbps or more is High quality, below is High efficiency`() {
        assertThat(
            label(AudioFormat("mp3", bitrateKbps = 320)),
        ).isEqualTo(QualityLabel(QualityKind.HighQuality, "MP3 · 320 kb/s"))
        assertThat(label(AudioFormat("aac", bitrateKbps = 256))?.kind).isEqualTo(QualityKind.HighQuality)
        assertThat(
            label(AudioFormat("aac", bitrateKbps = 128)),
        ).isEqualTo(QualityLabel(QualityKind.HighEfficiency, "AAC · 128 kb/s"))
    }

    @Test
    fun `a lossy file of unknown bitrate and an unknown format show no label`() {
        assertThat(label(AudioFormat("mp3"))).isNull()
        assertThat(label(null)).isNull()
    }

    @Test
    fun `a converted stream is Opus at its bitrate, whatever the original`() {
        val hiRes = AudioFormat("flac", sampleRateHz = 96_000, bitDepth = 24)
        assertThat(
            label(hiRes, convertedKbps = 256),
        ).isEqualTo(QualityLabel(QualityKind.HighQuality, "Opus · 256 kb/s"))
        assertThat(
            label(hiRes, convertedKbps = 128),
        ).isEqualTo(QualityLabel(QualityKind.HighEfficiency, "Opus · 128 kb/s"))
        // A converted stream with no known original still has a label.
        assertThat(label(null, convertedKbps = 256)?.kind).isEqualTo(QualityKind.HighQuality)
    }

    @Test
    fun `Dolby Atmos playing as it is says so, converted it is the conversion`() {
        val atmos =
            AudioFormat("eac3", sampleRateHz = 48_000, bitrateKbps = 768, spatialFormat = SpatialFormat.DolbyAtmos)

        assertThat(label(atmos)).isEqualTo(QualityLabel(QualityKind.DolbyAtmos, "E-AC-3 · 768 kb/s"))
        assertThat(label(atmos, convertedKbps = 256)?.kind).isEqualTo(QualityKind.HighQuality)
    }

    @Test
    fun `TrueHD and DSD are lossless`() {
        assertThat(label(AudioFormat("truehd", sampleRateHz = 48_000, bitDepth = 24))?.kind)
            .isEqualTo(QualityKind.HiResLossless)
        assertThat(label(AudioFormat("dsd_lsbf_planar", sampleRateHz = 2_822_400, bitDepth = 1)))
            .isEqualTo(QualityLabel(QualityKind.HiResLossless, "DSD · 1/2822.4"))
    }
}
