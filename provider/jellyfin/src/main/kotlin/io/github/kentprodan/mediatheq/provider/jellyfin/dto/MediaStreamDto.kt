package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One stream of a media file; we read the audio stream's format. */
@Serializable
internal data class MediaStreamDto(
    @SerialName("Type") val type: String? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("SampleRate") val sampleRate: Int? = null,
    @SerialName("BitDepth") val bitDepth: Int? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("BitRate") val bitRate: Int? = null,
    /** `None`, `DolbyAtmos` or `DTSX`. */
    @SerialName("AudioSpatialFormat") val audioSpatialFormat: String? = null,
)
