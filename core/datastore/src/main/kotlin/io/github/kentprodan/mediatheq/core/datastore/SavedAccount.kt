package io.github.kentprodan.mediatheq.core.datastore

import io.github.kentprodan.mediatheq.core.model.ServerId

/** A saved account without its token (#150): [signedIn] when a readable token is stored with it. */
data class SavedAccount(
    val id: String,
    val baseUrl: String,
    val serverId: ServerId,
    val serverName: String,
    val userId: String,
    val userName: String,
    val signedIn: Boolean = false,
)
