package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST /Users/AuthenticateWithQuickConnect`. */
@Serializable
internal data class QuickConnectDto(
    @SerialName("Secret") val secret: String,
)
