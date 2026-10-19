package io.github.kentprodan.mediatheq.core.server

import io.github.kentprodan.mediatheq.core.model.ServerId

/** The result of a successful login. */
data class AuthSession(
    val serverId: ServerId,
    val userId: String,
    val userName: String,
    val accessToken: String,
) {
    override fun toString(): String = "AuthSession(serverId=$serverId, userId=$userId, userName=$userName)"
}
