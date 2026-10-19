package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /QuickConnect/Initiate` and `GET /QuickConnect/Connect`. */
@Serializable
internal data class QuickConnectResultDto(
    @SerialName("Authenticated") val authenticated: Boolean = false,
    @SerialName("Secret") val secret: String? = null,
    @SerialName("Code") val code: String? = null,
)
