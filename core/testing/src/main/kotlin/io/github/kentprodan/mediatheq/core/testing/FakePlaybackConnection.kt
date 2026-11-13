package io.github.kentprodan.mediatheq.core.testing

import androidx.media3.common.Player
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.PlaybackError
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
        val startPosition: Duration = Duration.ZERO,
        val playlistId: MediaId? = null,
    )

    override val state = MutableStateFlow(PlayerUiModel())
    override val position = MutableStateFlow(Duration.ZERO)
    override val error = MutableStateFlow<PlaybackError?>(null)

    override fun dismissError() {
        error.value = null
    }

    val plays = mutableListOf<PlayCall>()

    override val expandRequested = MutableStateFlow(false)

    override fun acknowledgeExpand() {
        expandRequested.value = false
    }

    /** Every command other than [play], by name (e.g. `"seekTo 1m"`). */
    val commands = mutableListOf<String>()

    /** Each [playVideo], as the video and its start. */
    val videoCalls = mutableListOf<Pair<Video, Duration>>()

    override fun playVideo(
        video: Video,
        startPosition: Duration,
    ) {
        videoCalls += video to startPosition
    }

    override val videoPlayer = MutableStateFlow<Player?>(null)

    override fun play(
        queue: List<Track>,
        startIndex: Int,
        shuffle: Boolean,
        startPosition: Duration,
        playlistId: MediaId?,
    ) {
        plays += PlayCall(queue, startIndex, shuffle, startPosition, playlistId)
        expandRequested.value = true
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

    override fun setShuffle(enabled: Boolean) {
        commands += "shuffle $enabled"
    }

    override fun setRepeatMode(mode: RepeatMode) {
        commands += "repeat $mode"
    }

    override val queue = MutableStateFlow<List<QueueItem>>(emptyList())

    /** Tracks passed to [playNext] and [addToQueue], per call. */
    val playedNext = mutableListOf<List<Track>>()
    val addedToQueue = mutableListOf<List<Track>>()

    override fun playNext(tracks: List<Track>) {
        playedNext += tracks
        expandRequested.value = false
    }

    override fun addToQueue(tracks: List<Track>) {
        addedToQueue += tracks
        expandRequested.value = false
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
