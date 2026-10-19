package io.github.kentprodan.mediatheq.core.model

/** A music artist. */
data class Artist(
    val id: MediaId,
    val name: String,
    val sortName: String = name,
    val artwork: Artwork? = null,
    val userData: UserData = UserData(),
)
