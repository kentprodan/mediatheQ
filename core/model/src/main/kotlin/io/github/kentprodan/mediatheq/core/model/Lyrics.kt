package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration

/** One line of lyrics; [start] is when it is sung, `null` in plain lyrics. */
data class LyricLine(
    val text: String,
    val start: Duration? = null,
)

/** A track's lyrics. Synced lyrics have a [LyricLine.start] on every line, in order; plain lyrics on none. */
data class Lyrics(
    val lines: List<LyricLine>,
) {
    val isSynced: Boolean get() = lines.isNotEmpty() && lines.all { it.start != null }

    /** The line sung at [position], or -1 before the first line and for plain lyrics. */
    fun currentLineIndex(position: Duration): Int = if (isSynced) lines.indexOfLast { it.start!! <= position } else -1
}
