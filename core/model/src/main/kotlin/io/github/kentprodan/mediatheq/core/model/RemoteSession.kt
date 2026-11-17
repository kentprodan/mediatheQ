package io.github.kentprodan.mediatheq.core.model

/** Another device running mediatheQ that takes remote commands (#389): the TV the phone sends a movie to. */
data class RemoteSession(
    val id: String,
    val deviceName: String,
)
