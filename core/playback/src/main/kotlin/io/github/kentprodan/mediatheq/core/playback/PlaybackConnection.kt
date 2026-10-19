package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * How screens control playback (architecture skill, section 7). The player itself lives in the media service (#12);
 * commands are fire-and-forget, so they outlive the screen that sent them.
 */
interface PlaybackConnection {
    /** What the player is doing; collecting it connects to the player. */
    val state: StateFlow<PlayerUiModel>

    /** The playing position, sampled a few times a second while playing (so composables never poll). */
    val position: Flow<Duration>

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

    /** Pauses, or plays (also restarting after the queue ended or failed). */
    fun togglePlayPause()

    fun next()

    /** The previous track, or the start of this one when it played for a few seconds. */
    fun previous()

    fun seekTo(position: Duration)
}
