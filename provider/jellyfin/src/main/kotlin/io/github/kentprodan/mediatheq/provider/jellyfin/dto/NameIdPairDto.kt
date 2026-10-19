package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An artist reference inside an item. */
@Serializable
internal data class NameIdPairDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
)
