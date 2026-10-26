package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * How screens control playback (architecture skill, section 7). The player itself lives in the media service (#12);
 * commands are fire-and-forget, so they outlive the screen that sent them.
 */
interface PlaybackConnection {
    /** What the player is doing; collecting it connects to the player. */
    val state: StateFlow<PlayerUiModel>

    /**
     * The playing position, sampled every 100 ms while playing (so composables never poll); keeps the last
     * known value, so a screen that opens shows it at once.
     */
    val position: StateFlow<Duration>

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

    /** Turns shuffle on (the playing track goes on, the rest follows in a new random order) or off. */
    fun setShuffle(enabled: Boolean)

    fun setRepeatMode(mode: RepeatMode)

    /** The queue in play order; collecting it connects to the player. Changes only when the queue does. */
    val queue: StateFlow<List<QueueItem>>

    /** Queues [tracks] right after the current track; with nothing queued, plays them. */
    fun playNext(tracks: List<Track>)

    /** Queues [tracks] at the end; with nothing queued, plays them. */
    fun addToQueue(tracks: List<Track>)

    /** Moves the queue entry to position [to] of the queue (not shuffled: reordering needs shuffle off). */
    fun moveQueueItem(
        entryId: String,
        to: Int,
    )

    /** Removes the queue entry; removing the playing one goes on with the next. */
    fun removeQueueItem(entryId: String)

    /** Plays the queue entry from its start. */
    fun skipToQueueItem(entryId: String)
}
