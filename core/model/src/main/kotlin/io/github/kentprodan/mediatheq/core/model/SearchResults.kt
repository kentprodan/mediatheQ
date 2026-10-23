package io.github.kentprodan.mediatheq.core.model

/** What a search found, grouped by kind, each group best match first. */
data class SearchResults(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    fun isEmpty(): Boolean = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty() && playlists.isEmpty()
}
