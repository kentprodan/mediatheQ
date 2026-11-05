package io.github.kentprodan.mediatheq.core.model

/** A music genre (#130); its picture is the cover of its first album (the server has none, jellyfin R17). */
data class Genre(
    val id: MediaId,
    val name: String,
    val sortName: String = name,
    val albumCount: Int = 0,
    val trackCount: Int = 0,
    val artwork: Artwork? = null,
)
