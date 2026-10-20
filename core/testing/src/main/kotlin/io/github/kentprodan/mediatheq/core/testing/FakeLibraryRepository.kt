package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.SearchHit
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** [LibraryRepository] with settable contents; search matches names case-insensitively. */
class FakeLibraryRepository : LibraryRepository {
    val albums = MutableStateFlow<List<Album>>(emptyList())
    val albumArtists = MutableStateFlow<List<Artist>>(emptyList())
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val tracks = MutableStateFlow<List<Track>>(emptyList())
    var hits: List<SearchHit> = emptyList()

    override fun albums() = albums

    override fun album(id: MediaId) = albums.map { list -> list.firstOrNull { it.id == id } }

    /** Tracks of the album in the order they were set (set them in play order). */
    override fun albumTracks(albumId: MediaId) = tracks.map { list -> list.filter { it.albumId == albumId } }

    override fun albumArtists() = albumArtists

    /** Artists [artist] finds besides [albumArtists] (credit-only artists). */
    val creditedArtists = MutableStateFlow<List<Artist>>(emptyList())

    // Per-artist lists are set directly: the album artist vs track artist rules are proven by the DAO tests.
    val artistAlbums = MutableStateFlow<Map<MediaId, List<Album>>>(emptyMap())
    val appearsOn = MutableStateFlow<Map<MediaId, List<Album>>>(emptyMap())
    val artistTracks = MutableStateFlow<Map<MediaId, List<Track>>>(emptyMap())

    override fun artist(id: MediaId) =
        combine(albumArtists, creditedArtists) { a, c -> (a + c).firstOrNull { it.id == id } }

    override fun artistAlbums(artistId: MediaId) = artistAlbums.map { it[artistId].orEmpty() }

    override fun appearsOn(artistId: MediaId) = appearsOn.map { it[artistId].orEmpty() }

    override fun artistTracks(artistId: MediaId) = artistTracks.map { it[artistId].orEmpty() }

    override fun playlists() = playlists

    override fun tracks() = tracks

    override suspend fun search(
        query: String,
        limit: Int,
    ) = hits.filter { it.name.contains(query, ignoreCase = true) }.take(limit)
}
