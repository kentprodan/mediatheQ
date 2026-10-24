package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `Data` of a `UserDataChanged` message. */
@Serializable
internal data class UserDataChangeInfoDto(
    @SerialName("UserId") val userId: String? = null,
    @SerialName("UserDataList") val userDataList: List<UserItemDataDto> = emptyList(),
)
