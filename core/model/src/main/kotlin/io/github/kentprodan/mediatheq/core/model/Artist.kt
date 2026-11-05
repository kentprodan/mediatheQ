package io.github.kentprodan.mediatheq.core.model

/** A music artist. */
data class Artist(
    val id: MediaId,
    val name: String,
    val sortName: String = name,
    val artwork: Artwork? = null,
    val userData: UserData = UserData(),
    /** The wide fanart for the artist page (#133); `null` when the server has none. */
    val backdrop: Artwork? = null,
    /** The biography from the server, plain text. */
    val overview: String? = null,
)
