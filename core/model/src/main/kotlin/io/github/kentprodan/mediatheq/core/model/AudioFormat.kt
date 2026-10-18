package io.github.kentprodan.mediatheq.core.model

/** Facts about an audio stream; `null` means the server did not report it. */
data class AudioFormat(
    /** Codec as reported by the server, lower case (e.g. `flac`, `eac3`, `truehd`). */
    val codec: String,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val bitrateKbps: Int? = null,
    val spatialFormat: SpatialFormat? = null,
) {
    val isSpatial: Boolean get() = spatialFormat != null
}
