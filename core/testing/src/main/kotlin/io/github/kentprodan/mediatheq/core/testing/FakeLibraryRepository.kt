package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.SearchResults
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** [LibraryRepository] with settable contents; search matches names (and track titles) case-insensitively. */
class FakeLibraryRepository : LibraryRepository {
    val albums = MutableStateFlow<List<Album>>(emptyList())
    val albumArtists = MutableStateFlow<List<Artist>>(emptyList())
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val tracks = MutableStateFlow<List<Track>>(emptyList())

    /** Every query [search] was called with, in order. */
    val searches = mutableListOf<String>()

    /** When set, [search] fails with it. */
    var searchError: MqError? = null

    override fun albums() = albums

    override fun track(id: MediaId) = tracks.map { list -> list.firstOrNull { it.id == id } }

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
        limitPerKind: Int,
    ): MqResult<SearchResults> {
        searches += query
        searchError?.let { return MqResult.Failure(it) }
        val q = query.trim()
        if (q.isEmpty()) return MqResult.Success(SearchResults())

        fun String.matches() = contains(q, ignoreCase = true)
        return MqResult.Success(
            SearchResults(
                artists = albumArtists.value.filter { it.name.matches() }.take(limitPerKind),
                albums = albums.value.filter { it.title.matches() }.take(limitPerKind),
                tracks = tracks.value.filter { it.title.matches() }.take(limitPerKind),
                playlists = playlists.value.filter { it.name.matches() }.take(limitPerKind),
            ),
        )
    }
}
