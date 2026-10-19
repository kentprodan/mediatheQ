package io.github.kentprodan.mediatheq.core.model

/** One search result: what kind of item, which one, and its name. */
data class SearchHit(
    val kind: Kind,
    val id: MediaId,
    val name: String,
) {
    enum class Kind { Artist, Album, Track, Playlist }
}
