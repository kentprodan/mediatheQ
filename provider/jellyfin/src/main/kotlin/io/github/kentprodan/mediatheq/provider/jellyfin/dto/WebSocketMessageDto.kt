package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Envelope of every `/socket` message; [data] is read according to [messageType]. */
@Serializable
internal data class WebSocketMessageDto(
    @SerialName("MessageType") val messageType: String? = null,
    @SerialName("Data") val data: JsonElement? = null,
)
