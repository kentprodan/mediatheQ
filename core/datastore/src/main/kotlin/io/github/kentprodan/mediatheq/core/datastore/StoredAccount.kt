package io.github.kentprodan.mediatheq.core.datastore

import io.github.kentprodan.mediatheq.core.model.ServerId

/** The signed-in server account, with the access token already decrypted. */
data class StoredAccount(
    val baseUrl: String,
    val serverId: ServerId,
    val serverName: String,
    val userId: String,
    val userName: String,
    val accessToken: String,
) {
    override fun toString(): String =
        "StoredAccount(baseUrl=$baseUrl, serverId=$serverId, serverName=$serverName, userId=$userId, userName=$userName)"
}
