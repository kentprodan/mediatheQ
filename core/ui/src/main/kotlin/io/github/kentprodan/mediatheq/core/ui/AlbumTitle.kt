package io.github.kentprodan.mediatheq.core.ui

/** An album title split into its name and the edition it ends with (#132). */
data class AlbumTitle(
    val name: String,
    /** "Deluxe Edition" for "Abbey Road (Deluxe Edition)"; `null` when the title names no edition. */
    val version: String?,
)

/**
 * Splits a trailing edition in parentheses or brackets off [title]: "Bad (25th Anniversary)" -> "Bad" and
 * "25th Anniversary", "1989 [Taylor's Version]" -> "1989" and "Taylor's Version". Only editions count, so
 * "(feat. X)", "(Part 1)" or "(Interlude)" stay in the name, and a title that is only the parentheses stays whole.
 */
fun albumTitle(title: String): AlbumTitle {
    val match = TRAILING.find(title.trimEnd()) ?: return AlbumTitle(title, null)
    val name = title.substring(0, match.range.first).trimEnd()
    val inside = match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
    if (name.isEmpty() || !EDITION.containsMatchIn(inside) || FEATURING.containsMatchIn(inside)) {
        return AlbumTitle(title, null)
    }
    return AlbumTitle(name, inside)
}

private val TRAILING = Regex("""(?:\(([^()]*)\)|\[([^\[\]]*)])$""")

/** "(feat. Live Band)" names a guest, even when an edition word is in it. */
private val FEATURING = Regex("""^(feat\b|ft\b|featuring\b|with\b)""", RegexOption.IGNORE_CASE)

private val EDITION =
    Regex(
        "\\b(deluxe|edition|remaster(ed)?|expanded|anniversary|version|bonus|special|collector'?s|reissue|live|mono|" +
            "stereo|acoustic|extended|remix(es|ed)?|unplugged|demos?|instrumentals?)\\b",
        RegexOption.IGNORE_CASE,
    )
