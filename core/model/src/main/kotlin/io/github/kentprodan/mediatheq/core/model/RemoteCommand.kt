package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration

/** A command another client sends to this one through the server (#389): Jellyfin web, the phone to the TV. */
sealed interface RemoteCommand {
    data object Pause : RemoteCommand

    data object Unpause : RemoteCommand

    data object PlayPause : RemoteCommand

    data object Stop : RemoteCommand

    data object Next : RemoteCommand

    data object Previous : RemoteCommand

    data class Seek(
        val position: Duration,
    ) : RemoteCommand

    /** Play [items] now from [startIndex], the first one from [startPosition] (a movie from where the phone was). */
    data class Play(
        val items: List<MediaId>,
        val startIndex: Int = 0,
        val startPosition: Duration = Duration.ZERO,
    ) : RemoteCommand
}
