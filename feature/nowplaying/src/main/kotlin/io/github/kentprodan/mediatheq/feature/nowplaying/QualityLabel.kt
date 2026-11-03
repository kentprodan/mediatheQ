package io.github.kentprodan.mediatheq.feature.nowplaying

import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.SpatialFormat

/** What the quality label of Now Playing says (#125), from the best to the most compressed. */
enum class QualityKind { DolbyAtmos, HiResLossless, Lossless, HighQuality, HighEfficiency }

/** The label's kind, and the detail a tap shows instead ("FLAC · 24/96 · 1250 kb/s"). */
data class QualityLabel(
    val kind: QualityKind,
    val detail: String,
)

/**
 * The quality of what actually plays: the original file's [format], or the server's Opus conversion at
 * [convertedKbps]. `null` (no label) while the stream is not [streamKnown] yet, so a converted track never shows its
 * original quality for a moment, and when the facts are missing (no format, a lossy file of unknown bitrate).
 */
internal fun qualityLabel(
    format: AudioFormat?,
    streamKnown: Boolean,
    convertedKbps: Int?,
): QualityLabel? {
    if (!streamKnown) return null
    if (convertedKbps != null) return QualityLabel(lossyKind(convertedKbps), "Opus · $convertedKbps kb/s")
    format ?: return null
    val codec = format.codec.lowercase()
    val kind =
        when {
            // Only reached when the original plays: a converted Atmos track has a conversion bitrate.
            format.spatialFormat == SpatialFormat.DolbyAtmos -> QualityKind.DolbyAtmos

            codec.isLossless() -> if (format.isHiRes()) QualityKind.HiResLossless else QualityKind.Lossless

            else -> lossyKind(format.bitrateKbps ?: return null)
        }
    return QualityLabel(kind, format.detail())
}

private fun lossyKind(kbps: Int) =
    if (kbps >=
        HIGH_QUALITY_KBPS
    ) {
        QualityKind.HighQuality
    } else {
        QualityKind.HighEfficiency
    }

// Jellyfin names WAV and AIFF by their PCM sample format (pcm_s16le, pcm_s24be, ...).
private fun String.isLossless() = this in LOSSLESS_CODECS || startsWith("pcm_")

private fun AudioFormat.isHiRes() = (bitDepth ?: 0) > CD_BIT_DEPTH || (sampleRateHz ?: 0) > HI_RES_RATE_HZ

/** Codec · bit depth/sample rate (when the depth is known) · bitrate; missing parts are left out. */
private fun AudioFormat.detail(): String {
    val resolution = bitDepth?.let { depth -> sampleRateHz?.let { "$depth/${kiloHertz(it)}" } ?: "$depth bit" }
    return listOfNotNull(codecName(codec), resolution, bitrateKbps?.let { "$it kb/s" }).joinToString(" · ")
}

/** 44100 -> "44.1", 96000 -> "96". */
private fun kiloHertz(hz: Int): String {
    val tenths = (hz + 50) / 100
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

private fun codecName(codec: String): String {
    val lower = codec.lowercase()
    return CODEC_NAMES[lower] ?: if (lower.startsWith("pcm_")) "PCM" else lower.uppercase()
}

private val LOSSLESS_CODECS = setOf("flac", "alac", "ape", "wavpack", "wv", "wav", "aiff", "tta", "dsd")

private val CODEC_NAMES =
    mapOf(
        "flac" to "FLAC",
        "alac" to "ALAC",
        "ape" to "APE",
        "wavpack" to "WavPack",
        "wv" to "WavPack",
        "opus" to "Opus",
        "vorbis" to "Vorbis",
        "eac3" to "E-AC-3",
        "ac3" to "AC-3",
        "truehd" to "TrueHD",
        "dts" to "DTS",
    )

private const val HIGH_QUALITY_KBPS = 256
private const val CD_BIT_DEPTH = 16
private const val HI_RES_RATE_HZ = 48_000
