package io.github.kentprodan.mediatheq.provider.jellyfin.mapper

import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.LyricDto

/**
 * Synced when any line has a start, since `IsSynced` may be missing for `.lrc` files (R4): then the lines without one
 * are dropped and the rest sorted by start. Otherwise plain, in the server's order.
 */
internal fun LyricDto.toDomain(): Lyrics {
    val timed = lyrics.filter { it.start != null }
    return if (timed.isEmpty()) {
        Lyrics(lyrics.map { LyricLine(it.text) })
    } else {
        Lyrics(timed.sortedBy { it.start }.map { LyricLine(it.text, ticksToDuration(it.start)) })
    }
}
