package io.github.kentprodan.mediatheq.core.ui

import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import io.github.kentprodan.mediatheq.core.model.Track

/** What a quality label says (Now Playing #125, album page #132), from the best to the most compressed. */
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
fun qualityLabel(
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
private fun String.isLossless() = this in LOSSLESS_CODECS || startsWith("pcm_") || startsWith("dsd_")

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
    return CODEC_NAMES[lower] ?: when {
        lower.startsWith("pcm_") -> "PCM"
        lower.startsWith("dsd_") -> "DSD"
        else -> lower.uppercase()
    }
}

private val LOSSLESS_CODECS =
    setOf("flac", "alac", "ape", "wavpack", "wv", "wav", "aiff", "tta", "dsd", "truehd", "mlp")

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

/**
 * An album's quality (#132): its best track's kind (Dolby Atmos, then Hi-Res Lossless, Lossless, then lossy by
 * bitrate), so mixed formats show the highest. The detail is what the album page adds: "FLAC 24/96" for hi-res,
 * "MP3 320 kb/s" for lossy, nothing for Atmos and CD-quality lossless. `null` when no track has the facts.
 */
fun albumQuality(tracks: List<Track>): QualityLabel? {
    val (label, format) =
        tracks
            .mapNotNull { track ->
                track.audioFormat?.let { format ->
                    qualityLabel(format, streamKnown = true, convertedKbps = null)?.let { it to format }
                }
            }.minWithOrNull(
                compareBy<Pair<QualityLabel, AudioFormat>> { it.first.kind.ordinal }
                    .thenByDescending { it.second.bitDepth ?: 0 }
                    .thenByDescending { it.second.sampleRateHz ?: 0 }
                    .thenByDescending { it.second.bitrateKbps ?: 0 },
            ) ?: return null
    val codec = codecName(format.codec)
    val detail =
        when (label.kind) {
            QualityKind.HiResLossless -> {
                listOfNotNull(codec, format.bitDepth?.let { d -> format.sampleRateHz?.let { "$d/${kiloHertz(it)}" } })
                    .joinToString(" ")
            }

            QualityKind.HighQuality, QualityKind.HighEfficiency -> {
                listOfNotNull(codec, format.bitrateKbps?.let { "$it kb/s" }).joinToString(" ")
            }

            else -> {
                ""
            }
        }
    return QualityLabel(label.kind, detail)
}

/** The label's name ("Hi-Res Lossless"). */
val QualityKind.text: Int
    get() =
        when (this) {
            QualityKind.DolbyAtmos -> R.string.core_ui_quality_atmos
            QualityKind.HiResLossless -> R.string.core_ui_quality_hi_res
            QualityKind.Lossless -> R.string.core_ui_quality_lossless
            QualityKind.HighQuality -> R.string.core_ui_quality_high
            QualityKind.HighEfficiency -> R.string.core_ui_quality_efficient
        }
