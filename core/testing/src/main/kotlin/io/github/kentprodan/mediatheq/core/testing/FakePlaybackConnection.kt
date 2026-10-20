package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

/** Records every command; tests drive [state] and [position] directly. */
class FakePlaybackConnection : PlaybackConnection {
    data class PlayCall(
        val queue: List<Track>,
        val startIndex: Int,
        val shuffle: Boolean,
    )

    override val state = MutableStateFlow(PlayerUiModel())
    override val position = MutableStateFlow(Duration.ZERO)

    val plays = mutableListOf<PlayCall>()

    /** Every command other than [play], by name (e.g. `"seekTo 1m"`). */
    val commands = mutableListOf<String>()

    override fun play(
        queue: List<Track>,
        startIndex: Int,
        shuffle: Boolean,
    ) {
        plays += PlayCall(queue, startIndex, shuffle)
    }

    override fun togglePlayPause() {
        commands += "togglePlayPause"
    }

    override fun next() {
        commands += "next"
    }

    override fun previous() {
        commands += "previous"
    }

    override fun seekTo(position: Duration) {
        commands += "seekTo $position"
    }

    override val queue = MutableStateFlow<List<QueueItem>>(emptyList())

    /** Tracks passed to [playNext] and [addToQueue], per call. */
    val playedNext = mutableListOf<List<Track>>()
    val addedToQueue = mutableListOf<List<Track>>()

    override fun playNext(tracks: List<Track>) {
        playedNext += tracks
    }

    override fun addToQueue(tracks: List<Track>) {
        addedToQueue += tracks
    }

    override fun moveQueueItem(
        entryId: String,
        to: Int,
    ) {
        commands += "move $entryId $to"
    }

    override fun removeQueueItem(entryId: String) {
        commands += "remove $entryId"
    }

    override fun skipToQueueItem(entryId: String) {
        commands += "skipTo $entryId"
    }
}
