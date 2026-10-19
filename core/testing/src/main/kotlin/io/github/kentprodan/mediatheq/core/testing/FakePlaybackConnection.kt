package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection

/** Records every [play] call. */
class FakePlaybackConnection : PlaybackConnection {
    data class PlayCall(
        val queue: List<Track>,
        val startIndex: Int,
        val shuffle: Boolean,
    )

    val plays = mutableListOf<PlayCall>()

    override fun play(
        queue: List<Track>,
        startIndex: Int,
        shuffle: Boolean,
    ) {
        plays += PlayCall(queue, startIndex, shuffle)
    }
}
