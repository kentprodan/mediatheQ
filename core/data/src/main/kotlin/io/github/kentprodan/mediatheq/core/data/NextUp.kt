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

/** Where to start [video]: its resume point when it was left halfway, else from the start. */
fun Video.resumePosition(): Duration =
    userData.playbackPosition.takeIf { !userData.played && it > Duration.ZERO } ?: Duration.ZERO
