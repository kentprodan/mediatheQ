package io.github.kentprodan.mediatheq.core.model

/** A record label (#131), a Jellyfin "studio" of albums; its picture is its first album's cover (none on the server). */
data class RecordLabel(
    val id: MediaId,
    val name: String,
    val sortName: String = name,
    val albumCount: Int = 0,
    val artwork: Artwork? = null,
)
