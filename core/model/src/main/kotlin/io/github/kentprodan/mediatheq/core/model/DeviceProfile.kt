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
)

/** The format the server converts to when a file cannot be played as it is. */
data class TranscodingTarget(
    val container: String,
    val codec: String,
    val bitrate: Int,
    val maxChannels: Int,
    /** As HLS segments (seekable: each segment is converted on demand) instead of one live stream. */
    val segmented: Boolean = false,
)
