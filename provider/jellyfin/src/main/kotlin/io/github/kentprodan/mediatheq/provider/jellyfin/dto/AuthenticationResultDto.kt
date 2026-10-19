package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of both login endpoints. */
@Serializable
internal data class AuthenticationResultDto(
    @SerialName("User") val user: UserDto? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
) {
    override fun toString(): String = "AuthenticationResultDto(user=$user, serverId=$serverId)"
}
