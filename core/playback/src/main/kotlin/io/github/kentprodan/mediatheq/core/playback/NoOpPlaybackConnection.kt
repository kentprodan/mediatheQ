package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.model.Track
import javax.inject.Inject

// shortcut: plays nothing until the media service exists (#12); #12 replaces this class and its binding in place.
internal class NoOpPlaybackConnection
    @Inject
    constructor() : PlaybackConnection {
        override fun play(
            queue: List<Track>,
            startIndex: Int,
            shuffle: Boolean,
        ) = Unit
    }
