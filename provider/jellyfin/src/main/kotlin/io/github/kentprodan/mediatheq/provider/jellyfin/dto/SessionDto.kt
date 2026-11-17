package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A session of `GET /Sessions` (#389), what picking a device to send to needs. */
@Serializable
internal data class SessionDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Client") val client: String? = null,
    @SerialName("DeviceName") val deviceName: String? = null,
    @SerialName("DeviceId") val deviceId: String? = null,
    @SerialName("SupportsRemoteControl") val supportsRemoteControl: Boolean = false,
    @SerialName("PlayableMediaTypes") val playableMediaTypes: List<String> = emptyList(),
)
