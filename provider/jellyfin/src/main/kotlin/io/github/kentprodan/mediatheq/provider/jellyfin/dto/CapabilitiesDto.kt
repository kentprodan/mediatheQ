package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /Sessions/Capabilities/Full` (#389): what this session plays and that it takes remote commands. */
@Serializable
internal data class CapabilitiesDto(
    @SerialName("PlayableMediaTypes") val playableMediaTypes: List<String> = listOf("Audio", "Video"),
    @SerialName("SupportedCommands") val supportedCommands: List<String> = emptyList(),
    @SerialName("SupportsMediaControl") val supportsMediaControl: Boolean = true,
)

/** A `Playstate` message's data. */
@Serializable
internal data class PlaystateRequestDto(
    @SerialName("Command") val command: String? = null,
    @SerialName("SeekPositionTicks") val seekPositionTicks: Long? = null,
)

/** A `Play` message's data. */
@Serializable
internal data class PlayRequestDto(
    @SerialName("ItemIds") val itemIds: List<String> = emptyList(),
    @SerialName("StartPositionTicks") val startPositionTicks: Long? = null,
    @SerialName("PlayCommand") val playCommand: String? = null,
    @SerialName("StartIndex") val startIndex: Int? = null,
)
