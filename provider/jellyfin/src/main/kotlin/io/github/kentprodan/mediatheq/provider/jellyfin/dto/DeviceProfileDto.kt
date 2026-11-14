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
    @SerialName("CodecProfiles") val codecProfiles: List<CodecProfileDto>,
    @SerialName("ContainerProfiles") val containerProfiles: List<String>,
    @SerialName("SubtitleProfiles") val subtitleProfiles: List<String>,
)

@Serializable
internal data class DirectPlayProfileDto(
    @SerialName("Type") val type: String,
    @SerialName("Container") val container: String,
    @SerialName("AudioCodec") val audioCodec: String,
    @SerialName("VideoCodec") val videoCodec: String? = null,
)

@Serializable
internal data class TranscodingProfileDto(
    @SerialName("Type") val type: String,
    @SerialName("Container") val container: String,
    @SerialName("AudioCodec") val audioCodec: String,
    @SerialName("Protocol") val protocol: String,
    @SerialName("Context") val context: String,
    @SerialName("MaxAudioChannels") val maxAudioChannels: String,
    @SerialName("VideoCodec") val videoCodec: String? = null,
    /** Video HLS: segments may start between key frames, so a seek starts where asked (ADR 0050). */
    @SerialName("BreakOnNonKeyFrames") val breakOnNonKeyFrames: Boolean? = null,
    @SerialName("MinSegments") val minSegments: Int? = null,
)

/** Limits on a stream of [type] (`Video`, `VideoAudio`); a file outside them is converted. */
@Serializable
internal data class CodecProfileDto(
    @SerialName("Type") val type: String,
    @SerialName("Conditions") val conditions: List<ProfileConditionDto>,
)

@Serializable
internal data class ProfileConditionDto(
    @SerialName("Condition") val condition: String,
    @SerialName("Property") val property: String,
    @SerialName("Value") val value: String,
    @SerialName("IsRequired") val isRequired: Boolean,
)
