package io.github.kentprodan.mediatheq.core.playback

/** A track that could not play, for a message on screen (#61). */
data class PlaybackError(
    val title: String,
    val reason: Reason,
    /** The player went on with the next track; `false` when it stopped. */
    val skipped: Boolean,
) {
    enum class Reason {
        /** Offline, and the track is neither downloaded nor cached. */
        NotDownloaded,
        Failed,
    }
}
