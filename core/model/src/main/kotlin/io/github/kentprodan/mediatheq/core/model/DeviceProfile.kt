package io.github.kentprodan.mediatheq.core.model

/**
 * What this device asks the server for (media3 skill, section 3): formats it plays as they are, the format to convert
 * everything else to, and a bitrate cap. Names are server terms: containers `flac`, `ogg`; codecs `flac`, `opus`.
 */
data class DeviceProfile(
    val directPlayContainers: List<String>,
    val directPlayCodecs: List<String>,
    val transcoding: TranscodingTarget,
    /** Bits per second; `null` = no cap (original quality). */
    val maxStreamingBitrate: Int?,
    /**
     * Video codecs played as they are; non-`null` makes this a video profile (ADR 0050): [directPlayContainers] are then
     * video containers and [directPlayCodecs] the audio codecs inside them.
     */
    val videoCodecs: List<String>? = null,
    /** Taller videos are converted down (the video quality, ADR 0050); `null` = no limit. */
    val maxHeight: Int? = null,
)

/** The format the server converts to when a file cannot be played as it is. */
data class TranscodingTarget(
    val container: String,
    val codec: String,
    val bitrate: Int,
    val maxChannels: Int,
    /** As HLS segments (seekable: each segment is converted on demand) instead of one live stream. */
    val segmented: Boolean = false,
    /** The video codecs to convert to, best first (a video profile only); [codec] is then the audio codecs. */
    val videoCodec: String? = null,
)
