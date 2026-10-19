package io.github.kentprodan.mediatheq.core.server

/** Where and as whom a request goes. [deviceId] is the random per-install id; [accessToken] is `null` before login. */
data class ServerConnection(
    val baseUrl: String,
    val deviceId: String,
    val accessToken: String? = null,
) {
    override fun toString(): String = "ServerConnection(baseUrl=$baseUrl, hasToken=${accessToken != null})"
}
