package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/** Reads of the live library. Screens add their own queries in their issues. */
@Dao
interface LibraryDao {
    /** @Transaction: albums and their artists come from one snapshot, never from both sides of a swap. */
    @Transaction
    @Query("SELECT * FROM album ORDER BY sort_name, id")
    fun albums(): Flow<List<AlbumWithArtists>>

    /** The Library's Artists tab: album artists only (credit-only artists exist for track credits). */
    @Query("SELECT * FROM artist WHERE is_album_artist ORDER BY sort_name, id")
    fun albumArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM playlist ORDER BY sort_name, id")
    fun playlists(): Flow<List<PlaylistEntity>>

    /** All tracks with their artist credits, from one snapshot. */
    @Transaction
    @Query("SELECT * FROM track ORDER BY sort_name, id")
    fun tracks(): Flow<List<TrackWithArtists>>

    /** One track with its credits, `null` when it is not in the library. */
    @Transaction
    @Query("SELECT * FROM track WHERE server_id = :serverId AND id = :trackId")
    fun track(
        serverId: String,
        trackId: String,
    ): Flow<TrackWithArtists?>

    /** One album with its credits, `null` when it is not in the library. */
    @Transaction
    @Query("SELECT * FROM album WHERE server_id = :serverId AND id = :albumId")
    fun album(
        serverId: String,
        albumId: String,
    ): Flow<AlbumWithArtists?>

    /**
     * The tracks of one album in play order: disc (unknown = 1), then track number (unknown last), then name.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM track WHERE server_id = :serverId AND album_id = :albumId
        ORDER BY COALESCE(disc_number, 1), track_number IS NULL, track_number, sort_name, id
        """,
    )
    fun albumTracks(
        serverId: String,
        albumId: String,
    ): Flow<List<TrackWithArtists>>

    /** One artist, album artist or credit-only, `null` when it is not in the library. */
    @Query("SELECT * FROM artist WHERE server_id = :serverId AND id = :artistId")
    fun artist(
        serverId: String,
        artistId: String,
    ): Flow<ArtistEntity?>

    /** Albums the artist is an album artist of: newest year first, unknown year last. */
    @Transaction
    @Query(
        """
        SELECT * FROM album WHERE server_id = :serverId AND id IN (
            SELECT album_id FROM album_artist WHERE server_id = :serverId AND artist_id = :artistId
        )
        ORDER BY year IS NULL, year DESC, sort_name, id
        """,
    )
    fun artistAlbums(
        serverId: String,
        artistId: String,
    ): Flow<List<AlbumWithArtists>>

    /** Albums with a track credited to the artist, other than the artist's own albums; same order as [artistAlbums]. */
    @Transaction
    @Query(
        """
        SELECT * FROM album WHERE server_id = :serverId AND id IN (
            SELECT t.album_id FROM track t JOIN track_artist ta ON ta.server_id = t.server_id AND ta.track_id = t.id
            WHERE t.server_id = :serverId AND ta.artist_id = :artistId
        ) AND id NOT IN (
            SELECT album_id FROM album_artist WHERE server_id = :serverId AND artist_id = :artistId
        )
        ORDER BY year IS NULL, year DESC, sort_name, id
        """,
    )
    fun appearsOnAlbums(
        serverId: String,
        artistId: String,
    ): Flow<List<AlbumWithArtists>>

    /**
     * Every track of the artist, each once: the tracks of the artist's albums plus the tracks crediting the artist.
     * Own albums first, then other albums (both newest first), then tracks without an album; album play order within.
     */
    @Transaction
    @Query(
        """
        SELECT track.* FROM track
        LEFT JOIN album ON album.server_id = track.server_id AND album.id = track.album_id
        WHERE track.server_id = :serverId AND (
            track.album_id IN (SELECT album_id FROM album_artist WHERE server_id = :serverId AND artist_id = :artistId)
            OR EXISTS (
                SELECT 1 FROM track_artist ta
                WHERE ta.server_id = track.server_id AND ta.track_id = track.id AND ta.artist_id = :artistId
            )
        )
        ORDER BY track.album_id IS NULL,
            track.album_id NOT IN (
                SELECT album_id FROM album_artist WHERE server_id = :serverId AND artist_id = :artistId
            ),
            album.year IS NULL, album.year DESC, album.sort_name, track.album_id,
            COALESCE(track.disc_number, 1), track.track_number IS NULL, track.track_number, track.sort_name, track.id
        """,
    )
    fun artistTracks(
        serverId: String,
        artistId: String,
    ): Flow<List<TrackWithArtists>>

    /**
     * Best matches of each kind, from one snapshot: [match] must already be a safe FTS5 expression (see
     * `ftsMatchExpression` in `:core:data`). Ranked by bm25 (shorter names first), then by sort name.
     */
    @Transaction
    suspend fun search(
        match: String,
        limitPerKind: Int,
    ): SearchRows =
        SearchRows(
            artists = searchArtists(match, limitPerKind),
            albums = searchAlbums(match, limitPerKind),
            tracks = searchTracks(match, limitPerKind),
            playlists = searchPlaylists(match, limitPerKind),
        )

    @Query(
        """
        SELECT artist.* FROM search
        JOIN artist ON artist.server_id = search.server_id AND artist.id = search.item_id
        WHERE search MATCH :match AND search.kind = 'artist'
        ORDER BY search.rank, artist.sort_name, artist.id LIMIT :limit
        """,
    )
    suspend fun searchArtists(
        match: String,
        limit: Int,
    ): List<ArtistEntity>

    @Transaction
    @Query(
        """
        SELECT album.* FROM search
        JOIN album ON album.server_id = search.server_id AND album.id = search.item_id
        WHERE search MATCH :match AND search.kind = 'album'
        ORDER BY search.rank, album.sort_name, album.id LIMIT :limit
        """,
    )
    suspend fun searchAlbums(
        match: String,
        limit: Int,
    ): List<AlbumWithArtists>

    @Transaction
    @Query(
        """
        SELECT track.* FROM search
        JOIN track ON track.server_id = search.server_id AND track.id = search.item_id
        WHERE search MATCH :match AND search.kind = 'track'
        ORDER BY search.rank, track.sort_name, track.id LIMIT :limit
        """,
    )
    suspend fun searchTracks(
        match: String,
        limit: Int,
    ): List<TrackWithArtists>

    @Query(
        """
        SELECT playlist.* FROM search
        JOIN playlist ON playlist.server_id = search.server_id AND playlist.id = search.item_id
        WHERE search MATCH :match AND search.kind = 'playlist'
        ORDER BY search.rank, playlist.sort_name, playlist.id LIMIT :limit
        """,
    )
    suspend fun searchPlaylists(
        match: String,
        limit: Int,
    ): List<PlaylistEntity>
}

/** Search matches grouped by kind, each group best first. */
data class SearchRows(
    val artists: List<ArtistEntity>,
    val albums: List<AlbumWithArtists>,
    val tracks: List<TrackWithArtists>,
    val playlists: List<PlaylistEntity>,
)

/** An album with its artist credits; sort [credits] by position, Room does not order relations. */
data class AlbumWithArtists(
    @Embedded val album: AlbumEntity,
    @Relation(
        entity = AlbumArtistEntity::class,
        parentColumns = ["server_id", "id"],
        entityColumns = ["server_id", "album_id"],
    )
    val credits: List<AlbumCredit>,
)

/** A track with its artist credits; sort [credits] by position. */
data class TrackWithArtists(
    @Embedded val track: TrackEntity,
    @Relation(
        entity = TrackArtistEntity::class,
        parentColumns = ["server_id", "id"],
        entityColumns = ["server_id", "track_id"],
    )
    val credits: List<TrackCredit>,
)

/** One track credit and its artist; [artist] is `null` when the credited artist was not synced. */
data class TrackCredit(
    @Embedded val link: TrackArtistEntity,
    @Relation(parentColumns = ["server_id", "artist_id"], entityColumns = ["server_id", "id"])
    val artist: ArtistEntity?,
)

/** One album credit and its artist; [artist] is `null` when the credited artist was not synced. */
data class AlbumCredit(
    @Embedded val link: AlbumArtistEntity,
    @Relation(parentColumns = ["server_id", "artist_id"], entityColumns = ["server_id", "id"])
    val artist: ArtistEntity?,
)
