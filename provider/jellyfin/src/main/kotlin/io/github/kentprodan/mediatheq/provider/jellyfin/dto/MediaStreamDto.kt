package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One stream of a media file: the audio stream's format, and a video's streams (#270). */
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
    @SerialName("Index") val index: Int? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("IsForced") val isForced: Boolean = false,
    /** A subtitle file beside the video. */
    @SerialName("IsExternal") val isExternal: Boolean = false,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    /** `SDR`, `HDR10`, `HDR10Plus`, `HLG`, `DOVI`, `DOVIWithHDR10`, `DOVIWithHLG`, `DOVIWithSDR`, `Unknown`. */
    @SerialName("VideoRangeType") val videoRangeType: String? = null,
)
