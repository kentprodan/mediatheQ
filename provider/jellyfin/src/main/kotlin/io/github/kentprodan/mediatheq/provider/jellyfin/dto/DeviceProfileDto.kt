package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The device profile as Jellyfin expects it; lists are comma-separated strings. */
@Serializable
internal data class DeviceProfileDto(
    @SerialName("Name") val name: String,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int?,
    /** Without it Jellyfin converts music at 128 kbps whatever the cap (checked on 12.2.0). */
    @SerialName("MusicStreamingTranscodingBitrate") val musicStreamingTranscodingBitrate: Int,
    @SerialName("DirectPlayProfiles") val directPlayProfiles: List<DirectPlayProfileDto>,
    @SerialName("TranscodingProfiles") val transcodingProfiles: List<TranscodingProfileDto>,
    @SerialName("CodecProfiles") val codecProfiles: List<String>,
    @SerialName("ContainerProfiles") val containerProfiles: List<String>,
    @SerialName("SubtitleProfiles") val subtitleProfiles: List<String>,
)

@Serializable
internal data class DirectPlayProfileDto(
    @SerialName("Type") val type: String,
    @SerialName("Container") val container: String,
    @SerialName("AudioCodec") val audioCodec: String,
)

@Serializable
internal data class TranscodingProfileDto(
    @SerialName("Type") val type: String,
    @SerialName("Container") val container: String,
    @SerialName("AudioCodec") val audioCodec: String,
    @SerialName("Protocol") val protocol: String,
    @SerialName("Context") val context: String,
    @SerialName("MaxAudioChannels") val maxAudioChannels: String,
)
