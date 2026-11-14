package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.ResolvedTracks
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.SubtitleMode
import io.github.kentprodan.mediatheq.core.model.TrackChoice
import io.github.kentprodan.mediatheq.core.model.TrackDefaults
import java.util.Locale

/**
 * The tracks a video plays with (ADR 0050), audio and subtitles each on its own: the item's own choice when its index is
 * still one of [streams], else the season's choice found again by language, else [defaults]. Pure.
 */
fun resolveChoice(
    streams: List<MediaStreamInfo>,
    item: TrackChoice?,
    season: TrackChoice?,
    defaults: TrackDefaults,
): ResolvedTracks {
    val audio = streams.filter { it.type == StreamType.Audio }
    val subtitles = streams.filter { it.type == StreamType.Subtitle }

    val audioIndex =
        item?.audioIndex?.takeIf { index -> audio.any { it.index == index } }
            ?: season?.let { audio.sameStream(it.audioIndex, it.audioLanguage) }
            ?: defaults.audioLanguage?.let { language -> audio.byLanguage(language)?.index }
    val audioLanguage =
        audio.firstOrNull { it.index == audioIndex }?.language ?: audio.firstOrNull { it.isDefault }?.language

    val subtitleIndex =
        when {
            item?.subtitleOff == true -> {
                null
            }

            item?.subtitleIndex != null && subtitles.any { it.index == item.subtitleIndex } -> {
                item.subtitleIndex
            }

            season?.subtitleOff == true -> {
                null
            }

            season != null && (season.subtitleIndex != null || season.subtitleLanguage != null) -> {
                subtitles.sameStream(season.subtitleIndex, season.subtitleLanguage)
            }

            else -> {
                defaults.subtitle(subtitles, audioLanguage)
            }
        }
    return ResolvedTracks(audioIndex, subtitleIndex)
}

/** Settings' subtitle rule: none, a forced one (in the audio's language when there is one), or the chosen language. */
private fun TrackDefaults.subtitle(
    subtitles: List<MediaStreamInfo>,
    audioLanguage: String?,
): Int? =
    when (subtitleMode) {
        SubtitleMode.Off -> {
            null
        }

        SubtitleMode.ForcedOnly -> {
            // Forced subtitles translate what the audio leaves untranslated: only those in the audio's language.
            val forced = subtitles.filter { it.isForced }
            if (audioLanguage == null) {
                forced.firstOrNull()?.index
            } else {
                forced.firstOrNull { sameLanguage(it.language, audioLanguage) }?.index
            }
        }

        SubtitleMode.Language -> {
            subtitleLanguage?.let { subtitles.byLanguage(it)?.index }
        }
    }

/**
 * A season's choice in another episode: the stream at the same [index] when it has the same language (episodes of a
 * season usually share their layout, so a forced or SDH pick stays one), else one in [language].
 */
private fun List<MediaStreamInfo>.sameStream(
    index: Int?,
    language: String?,
): Int? {
    val atIndex = firstOrNull { it.index == index }
    if (atIndex != null && (language == null || sameLanguage(atIndex.language, language))) return atIndex.index
    return language?.let { byLanguage(it)?.index }
}

/** The stream in [language]: a full (not forced) one first, the default among them first. */
private fun List<MediaStreamInfo>.byLanguage(language: String): MediaStreamInfo? =
    filter {
        sameLanguage(
            it.language,
            language,
        )
    }.sortedWith(compareBy({ it.isForced }, { !it.isDefault })).firstOrNull()

/**
 * Whether two ISO 639 codes name one language: "ro", "ron" and "rum" do (the server uses either three-letter form, the
 * phone's locale the two-letter one).
 */
fun sameLanguage(
    a: String?,
    b: String?,
): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return false
    return normalized(a) == normalized(b)
}

private fun normalized(code: String): String {
    val lower = code.lowercase(Locale.ROOT)
    val terminology = BIBLIOGRAPHIC[lower] ?: lower
    if (terminology.length != 2) return terminology
    return runCatching { Locale.forLanguageTag(terminology).isO3Language }.getOrNull()?.ifEmpty { null } ?: terminology
}

/** ISO 639-2 bibliographic codes and their terminology twins (what `Locale` gives). */
private val BIBLIOGRAPHIC =
    mapOf(
        "rum" to "ron",
        "fre" to "fra",
        "ger" to "deu",
        "dut" to "nld",
        "chi" to "zho",
        "cze" to "ces",
        "gre" to "ell",
        "per" to "fas",
        "slo" to "slk",
        "alb" to "sqi",
        "arm" to "hye",
        "baq" to "eus",
        "bur" to "mya",
        "geo" to "kat",
        "ice" to "isl",
        "mac" to "mkd",
        "mao" to "mri",
        "may" to "msa",
        "tib" to "bod",
        "wel" to "cym",
    )
