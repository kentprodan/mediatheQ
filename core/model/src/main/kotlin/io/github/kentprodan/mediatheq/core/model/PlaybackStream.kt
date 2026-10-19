package io.github.kentprodan.mediatheq.core.model

/** How the server delivers a track. */
enum class PlayMethod { DirectPlay, DirectStream, Transcode }

/**
 * A playable stream for one track. [url] never contains the access token (it goes in a header); [playSessionId] and
 * [mediaSourceId] identify the session in progress reports.
 */
data class PlaybackStream(
    val itemId: MediaId,
    val url: String,
    val playMethod: PlayMethod,
    val mediaSourceId: String,
    val playSessionId: String,
    /** Live transcodes cannot be seeked (the server sends no byte ranges). */
    val canSeek: Boolean,
)
