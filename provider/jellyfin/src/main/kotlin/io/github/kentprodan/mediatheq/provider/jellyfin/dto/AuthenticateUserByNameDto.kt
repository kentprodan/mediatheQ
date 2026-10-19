package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST /Users/AuthenticateByName`. */
@Serializable
internal data class AuthenticateUserByNameDto(
    @SerialName("Username") val userName: String,
    @SerialName("Pw") val password: String,
) {
    override fun toString(): String = "AuthenticateUserByNameDto(userName=$userName)"
}
