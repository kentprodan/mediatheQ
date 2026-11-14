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
    /** Live progressive transcodes cannot be seeked (the server sends no byte ranges); HLS and files can. */
    val canSeek: Boolean,
    /** The original file's size for a direct play, to spot a cached copy of a file changed on the server. */
    val sizeBytes: Long? = null,
    /** [url] is an HLS playlist (a converted stream in segments), not a file. */
    val segmented: Boolean = false,
    /** A video's subtitle streams and how each is delivered (#274); empty for music. */
    val subtitles: List<SubtitleDelivery> = emptyList(),
    /** A video's audio streams in the file's order (#274): a direct play selects the chosen one by its place. */
    val audioStreams: List<MediaStreamInfo> = emptyList(),
    /** The audio and subtitle streams asked for (#274), carried into every report; `null` = the server's default / none. */
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
)
