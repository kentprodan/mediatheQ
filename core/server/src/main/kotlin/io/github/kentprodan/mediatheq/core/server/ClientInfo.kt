package io.github.kentprodan.mediatheq.core.server

/** How this app introduces itself to servers, e.g. client "mediatheQ", device "Pixel 9", version "0.1.0". */
data class ClientInfo(
    val client: String,
    val device: String,
    val version: String,
)
