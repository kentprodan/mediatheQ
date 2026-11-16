package io.github.kentprodan.mediatheq.provider.lrclib

import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// [mm:ss.xx], [mm:ss.xxx] or [mm:ss]; a line may carry several stamps.
private val STAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

/** Synced lyrics from LRC text: one line per time stamp, in time order; `null` when no line has one. */
internal fun parseLrc(text: String): Lyrics? {
    val lines =
        text
            .lineSequence()
            .flatMap { raw ->
                val words = raw.replace(STAMP, "").trim()
                STAMP.findAll(raw).map { LyricLine(words, it.toDuration()) }
            }.sortedBy { it.start }
            .toList()
    return lines.takeIf { it.isNotEmpty() }?.let(::Lyrics)
}

/** Plain lyrics: one line per text line, blank lines kept as breaks; `null` when there is no text. */
internal fun plainLyrics(text: String): Lyrics? =
    text
        .trim()
        .lines()
        .map { LyricLine(it.trim()) }
        .takeIf { lines -> lines.any { it.text.isNotEmpty() } }
        ?.let(::Lyrics)

private fun MatchResult.toDuration(): Duration {
    val (min, sec, fraction) = destructured
    // ".5" is half a second, ".05" five hundredths, ".005" five thousandths.
    val millis = if (fraction.isEmpty()) 0 else fraction.padEnd(3, '0').toInt()
    return min.toInt().minutes + sec.toInt().seconds + millis.milliseconds
}
