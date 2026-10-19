package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.model.Track

/**
 * How screens control playback (architecture skill, section 7). The player itself lives in the media service (#12);
 * commands are fire-and-forget, so they outlive the screen that sent them.
 */
interface PlaybackConnection {
    /**
     * Replaces the queue with [queue] and starts playing it at [startIndex]. With [shuffle] the player turns shuffle on
     * and picks a random first track itself (it ignores [startIndex] and keeps the original order for unshuffling);
     * without it, shuffle is turned off.
     */
    fun play(
        queue: List<Track>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
    )
}
