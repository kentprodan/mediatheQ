package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.Video
import kotlin.time.Duration

/**
 * The episode to watch next (#273) from a series' [episodes] in watching order: the first one not watched after the last
 * watched one (one left halfway counts as not watched, and then resumes); the first episode when none was watched;
 * `null` when nothing follows the last watched one. An earlier unwatched episode (a gap) does not count: people skip
 * episodes on purpose.
 */
fun nextUp(episodes: List<Video>): Video? {
    val lastWatched = episodes.indexOfLast { it.userData.played }
    return episodes.drop(lastWatched + 1).firstOrNull { !it.userData.played }
}

/**
 * Next up across series (Home, #275) from [episodes] grouped by series in watching order: each started series' [nextUp]
 * (a series none of whose episodes was watched or begun is not in progress), the series played last first.
 */
fun nextUpPerSeries(episodes: List<Video>): List<Video> =
    episodes
        .groupBy { it.seriesId }
        .values
        .filter { series -> series.any { it.userData.played || it.userData.playbackPosition > Duration.ZERO } }
        .mapNotNull { series ->
            nextUp(series)?.let { it to series.mapNotNull { e -> e.userData.lastPlayedAt }.maxOrNull() }
        }.sortedByDescending { it.second }
        .map { it.first }

/**
 * The episode after [current] in its series' [episodes] (watching order), for auto-play (#275): the next one of the
 * season, else the first of the next season; never a special (season 0); `null` after the last.
 */
fun nextEpisode(
    episodes: List<Video>,
    current: Video,
): Video? {
    val index = episodes.indexOfFirst { it.id == current.id }
    if (index < 0) return null
    return episodes.drop(index + 1).firstOrNull { it.seasonNumber != 0 }
}

/** Where to start [video]: its resume point when it was left halfway, else from the start. */
fun Video.resumePosition(): Duration =
    userData.playbackPosition.takeIf { !userData.played && it > Duration.ZERO } ?: Duration.ZERO
