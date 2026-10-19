package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.SearchHit
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow

/** [LibraryRepository] with settable contents; search matches names case-insensitively. */
class FakeLibraryRepository : LibraryRepository {
    val albums = MutableStateFlow<List<Album>>(emptyList())
    val albumArtists = MutableStateFlow<List<Artist>>(emptyList())
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val tracks = MutableStateFlow<List<Track>>(emptyList())
    var hits: List<SearchHit> = emptyList()

    override fun albums() = albums

    override fun albumArtists() = albumArtists

    override fun playlists() = playlists

    override fun tracks() = tracks

    override suspend fun search(
        query: String,
        limit: Int,
    ) = hits.filter { it.name.contains(query, ignoreCase = true) }.take(limit)
}
