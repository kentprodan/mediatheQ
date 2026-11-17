package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An item's person (`Fields=People`). */
@Serializable
data class PersonDto(
    @SerialName("Name") val name: String? = null,
    @SerialName("Id") val id: String? = null,
    @SerialName("Role") val role: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)
