package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.dao.LibraryDao
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.SearchHit
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultLibraryRepository
    @Inject
    constructor(
        private val libraryDao: LibraryDao,
    ) : LibraryRepository {
        override fun albums(): Flow<List<Album>> = libraryDao.albums().map { rows -> rows.map { it.toDomain() } }

        override fun album(id: MediaId): Flow<Album?> =
            libraryDao.album(id.serverId.value, id.itemId).map { it?.toDomain() }

        override fun albumTracks(albumId: MediaId): Flow<List<Track>> =
            libraryDao.albumTracks(albumId.serverId.value, albumId.itemId).map { rows -> rows.map { it.toDomain() } }

        override fun albumArtists(): Flow<List<Artist>> =
            libraryDao.albumArtists().map { rows -> rows.map { it.toDomain() } }

        override fun artist(id: MediaId): Flow<Artist?> =
            libraryDao.artist(id.serverId.value, id.itemId).map { it?.toDomain() }

        override fun artistAlbums(artistId: MediaId): Flow<List<Album>> =
            libraryDao.artistAlbums(artistId.serverId.value, artistId.itemId).map { rows -> rows.map { it.toDomain() } }

        override fun appearsOn(artistId: MediaId): Flow<List<Album>> =
            libraryDao.appearsOnAlbums(artistId.serverId.value, artistId.itemId).map { rows ->
                rows.map { it.toDomain() }
            }

        override fun artistTracks(artistId: MediaId): Flow<List<Track>> =
            libraryDao.artistTracks(artistId.serverId.value, artistId.itemId).map { rows -> rows.map { it.toDomain() } }

        override fun playlists(): Flow<List<Playlist>> =
            libraryDao.playlists().map { rows -> rows.map { it.toDomain() } }

        override fun tracks(): Flow<List<Track>> = libraryDao.tracks().map { rows -> rows.map { it.toDomain() } }

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<SearchHit> {
            val match = ftsMatchExpression(query) ?: return emptyList()
            return libraryDao.search(match, limit).mapNotNull { row ->
                val kind = SearchHit.Kind.entries.firstOrNull { it.name.equals(row.kind, ignoreCase = true) }
                kind?.let { SearchHit(it, MediaId(ServerId(row.serverId), row.itemId), row.name) }
            }
        }
    }
