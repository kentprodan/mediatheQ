package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Per-user state of an item. */
@Serializable
internal data class UserItemDataDto(
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("Rating") val rating: Double? = null,
    @SerialName("PlayCount") val playCount: Int = 0,
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
    @SerialName("Played") val played: Boolean = false,
    /** Set in WebSocket `UserDataChanged` messages, which carry no item around it. */
    @SerialName("ItemId") val itemId: String? = null,
)
