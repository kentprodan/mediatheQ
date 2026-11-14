package io.github.kentprodan.mediatheq.core.model

/**
 * The audio and subtitle tracks chosen for a movie, an episode or a whole season (ADR 0050, #274). Indexes are the
 * server's stream indexes of the item it was made on; the languages let a season's choice find the same tracks in its
 * other episodes. `null` fields were not chosen.
 */
data class TrackChoice(
    val audioIndex: Int? = null,
    val audioLanguage: String? = null,
    val subtitleIndex: Int? = null,
    val subtitleLanguage: String? = null,
    /** Subtitles turned off on purpose (not merely not chosen). */
    val subtitleOff: Boolean = false,
)

/** When subtitles show without a choice (Settings -> Video, #276): never, only forced ones, or in a language. */
enum class SubtitleMode { Off, ForcedOnly, Language }

/** The defaults for everything without a choice; ISO 639 codes, two or three letters. */
data class TrackDefaults(
    val audioLanguage: String? = null,
    val subtitleMode: SubtitleMode = SubtitleMode.ForcedOnly,
    val subtitleLanguage: String? = null,
)

/** What to ask the server for: stream indexes, `null` audio = the server's default, `null` subtitle = none. */
data class ResolvedTracks(
    val audioIndex: Int?,
    val subtitleIndex: Int?,
)
