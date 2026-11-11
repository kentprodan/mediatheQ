package io.github.kentprodan.mediatheq.core.datastore

import io.github.kentprodan.mediatheq.core.common.DEFAULT_DATABASE
import io.github.kentprodan.mediatheq.core.model.ServerId

/** A saved server account, with the access token already decrypted; [id] is its database file name (#147). */
data class StoredAccount(
    val baseUrl: String,
    val serverId: ServerId,
    val serverName: String,
    val userId: String,
    val userName: String,
    val accessToken: String,
    val id: String = DEFAULT_DATABASE,
) {
    override fun toString(): String =
        "StoredAccount(id=$id, baseUrl=$baseUrl, serverId=$serverId, serverName=$serverName, userId=$userId, " +
            "userName=$userName)"
}
