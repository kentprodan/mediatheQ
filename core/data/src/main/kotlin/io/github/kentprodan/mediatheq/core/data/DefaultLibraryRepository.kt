package io.github.kentprodan.mediatheq.core.data

import androidx.sqlite.SQLiteException
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.database.dao.LibraryDao
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.SearchResults
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultLibraryRepository
    @Inject
    constructor(
        private val libraryDao: LibraryDao,
        private val filter: OfflineFilter,
    ) : LibraryRepository {
        override val offline: Flow<Boolean> = filter.active

        override fun albums(): Flow<List<Album>> =
            filter.of(libraryDao::albums).map { rows -> rows.map { it.toDomain() } }

        override fun track(id: MediaId): Flow<Track?> =
            libraryDao.track(id.serverId.value, id.itemId).map { it?.toDomain() }

        override fun album(id: MediaId): Flow<Album?> =
            libraryDao.album(id.serverId.value, id.itemId).map { it?.toDomain() }

        override fun albumTracks(albumId: MediaId): Flow<List<Track>> =
            filter
                .of {
                    all,
                    downloaded,
                    ->
                    libraryDao.albumTracks(albumId.serverId.value, albumId.itemId, all, downloaded)
                }.map { rows -> rows.map { it.toDomain() } }

        override fun albumArtists(): Flow<List<Artist>> =
            filter.of(libraryDao::albumArtists).map { rows -> rows.map { it.toDomain() } }

        override fun artist(id: MediaId): Flow<Artist?> =
            libraryDao.artist(id.serverId.value, id.itemId).map { it?.toDomain() }

        override fun artistAlbums(artistId: MediaId): Flow<List<Album>> =
            filter
                .of {
                    all,
                    downloaded,
                    ->
                    libraryDao.artistAlbums(artistId.serverId.value, artistId.itemId, all, downloaded)
                }.map { rows -> rows.map { it.toDomain() } }

        override fun appearsOn(artistId: MediaId): Flow<List<Album>> =
            filter
                .of { all, downloaded ->
                    libraryDao.appearsOnAlbums(artistId.serverId.value, artistId.itemId, all, downloaded)
                }.map { rows -> rows.map { it.toDomain() } }

        override fun artistTracks(artistId: MediaId): Flow<List<Track>> =
            filter
                .of {
                    all,
                    downloaded,
                    ->
                    libraryDao.artistTracks(artistId.serverId.value, artistId.itemId, all, downloaded)
                }.map { rows -> rows.map { it.toDomain() } }

        override fun playlists(): Flow<List<Playlist>> =
            filter.of(libraryDao::playlists).map { rows -> rows.map { it.toDomain() } }

        override fun tracks(): Flow<List<Track>> =
            filter.of(libraryDao::tracks).map { rows -> rows.map { it.toDomain() } }

        override suspend fun search(
            query: String,
            limitPerKind: Int,
        ): MqResult<SearchResults> {
            val match = ftsMatchExpression(query) ?: return MqResult.Success(SearchResults())
            val rows =
                try {
                    filter.once { all, downloaded -> libraryDao.search(match, limitPerKind, all, downloaded) }
                } catch (_: SQLiteException) {
                    return MqResult.Failure(MqError.Storage)
                }
            return MqResult.Success(
                SearchResults(
                    artists = rows.artists.map { it.toDomain() },
                    albums = rows.albums.map { it.toDomain() },
                    tracks = rows.tracks.map { it.toDomain() },
                    playlists = rows.playlists.map { it.toDomain() },
                ),
            )
        }
    }
