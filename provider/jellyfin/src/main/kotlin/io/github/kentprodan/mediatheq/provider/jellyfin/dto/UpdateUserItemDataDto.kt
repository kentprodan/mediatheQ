package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST /UserItems/{itemId}/UserData`; fields left out (null) keep their value on the server. */
@Serializable
internal data class UpdateUserItemDataDto(
    @SerialName("IsFavorite") val isFavorite: Boolean? = null,
    @SerialName("Rating") val rating: Double? = null,
)
