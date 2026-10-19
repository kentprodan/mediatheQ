package io.github.kentprodan.mediatheq.core.server

import io.github.kentprodan.mediatheq.core.model.ServerId

/** A server account with a token: what library reads need. [connection] carries the access token. */
data class SignedInServer(
    val connection: ServerConnection,
    val serverId: ServerId,
    val userId: String,
)
