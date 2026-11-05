package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumLabelEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.ImageColumns
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads of the live library. Screens add their own queries in their issues.
 *
 * Lists take `all` and `downloaded` (#63): with `all` false they keep only what is on the device: tracks whose item
 * id is in `downloaded`, and albums, artists and playlists holding such a track. Each id is a bound variable, so
 * `downloaded` must stay below SQLite's 32766 (the library is under 10k tracks, ADR 0021).
 */
@Dao
interface LibraryDao {
    /** @Transaction: albums and their artists come from one snapshot, never from both sides of a swap. */
    @Transaction
    @Query(
        """
        SELECT * FROM album WHERE (:all OR EXISTS (
            SELECT 1 FROM track d
            WHERE d.server_id = album.server_id AND d.album_id = album.id AND d.id IN (:downloaded)
        ))
        ORDER BY sort_name, id
        """,
    )
    fun albums(
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<AlbumWithArtists>>

    /** The Library's Artists tab: album artists only (credit-only artists exist for track credits). */
    @Query(
        """
        SELECT * FROM artist WHERE is_album_artist AND (:all OR EXISTS (
            SELECT 1 FROM album_artist aa JOIN track d ON d.server_id = aa.server_id AND d.album_id = aa.album_id
            WHERE aa.server_id = artist.server_id AND aa.artist_id = artist.id AND d.id IN (:downloaded)
        ))
        ORDER BY sort_name, id
        """,
    )
    fun albumArtists(
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<ArtistEntity>>

    @Query(
        """
        SELECT * FROM playlist WHERE (:all OR EXISTS (
            SELECT 1 FROM playlist_entry e
            WHERE e.server_id = playlist.server_id AND e.playlist_id = playlist.id AND e.track_id IN (:downloaded)
        ))
        ORDER BY sort_name, id
        """,
    )
    fun playlists(
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<PlaylistEntity>>

    /**
     * The genres (#130), each with its album and track counts and the cover of its first album (by name) as its picture.
     * Albums and tracks belong to the genres they are tagged with; offline a genre counts only its downloaded tracks and
     * the albums holding one, and a genre with none of them is left out.
     */
    @Query(
        """
        WITH
        ga AS (
            SELECT ag.server_id, ag.genre_id, ag.album_id FROM album_genre ag
            WHERE :all OR EXISTS (
                SELECT 1 FROM track d WHERE d.server_id = ag.server_id AND d.album_id = ag.album_id AND d.id IN (:downloaded)
            )
        ),
        gt AS (SELECT server_id, genre_id, track_id FROM track_genre WHERE :all OR track_id IN (:downloaded)),
        names AS (
            SELECT server_id, genre_id, MAX(genre_name) AS name FROM (
                SELECT server_id, genre_id, genre_name FROM album_genre
                UNION ALL SELECT server_id, genre_id, genre_name FROM track_genre
            ) GROUP BY server_id, genre_id
        ),
        cover AS (
            SELECT ga.server_id, ga.genre_id, a.image_item_id, a.image_tag, a.blur_hash,
                ROW_NUMBER() OVER (PARTITION BY ga.server_id, ga.genre_id ORDER BY a.sort_name, a.id) AS rn
            FROM ga JOIN album a ON a.server_id = ga.server_id AND a.id = ga.album_id
            WHERE a.image_tag IS NOT NULL
        )
        SELECT * FROM (
            SELECT n.server_id, n.genre_id AS id, n.name,
                (SELECT COUNT(*) FROM ga WHERE ga.server_id = n.server_id AND ga.genre_id = n.genre_id) AS album_count,
                (SELECT COUNT(*) FROM gt WHERE gt.server_id = n.server_id AND gt.genre_id = n.genre_id) AS track_count,
                c.image_item_id, c.image_tag, c.blur_hash
            FROM names n LEFT JOIN cover c ON c.server_id = n.server_id AND c.genre_id = n.genre_id AND c.rn = 1
        ) WHERE album_count > 0 OR track_count > 0
        ORDER BY name COLLATE NOCASE, id
        """,
    )
    fun genres(
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<GenreRow>>

    /**
     * The record labels (#131), each with its album count and the cover of its first album (by name) as its picture.
     * Offline a label counts only the albums holding a downloaded track, and a label with none is left out.
     */
    @Query(
        """
        WITH
        la AS (
            SELECT al.server_id, al.label_id, al.album_id, al.label_name FROM album_label al
            WHERE :all OR EXISTS (
                SELECT 1 FROM track d WHERE d.server_id = al.server_id AND d.album_id = al.album_id AND d.id IN (:downloaded)
            )
        ),
        cover AS (
            SELECT la.server_id, la.label_id, a.image_item_id, a.image_tag, a.blur_hash,
                ROW_NUMBER() OVER (PARTITION BY la.server_id, la.label_id ORDER BY a.sort_name, a.id) AS rn
            FROM la JOIN album a ON a.server_id = la.server_id AND a.id = la.album_id
            WHERE a.image_tag IS NOT NULL
        )
        SELECT l.server_id, l.label_id AS id, l.name, l.album_count, c.image_item_id, c.image_tag, c.blur_hash
        FROM (
            SELECT server_id, label_id, MAX(label_name) AS name, COUNT(*) AS album_count FROM la
            GROUP BY server_id, label_id
        ) l LEFT JOIN cover c ON c.server_id = l.server_id AND c.label_id = l.label_id AND c.rn = 1
        ORDER BY l.name COLLATE NOCASE, l.label_id
        """,
    )
    fun recordLabels(
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<RecordLabelRow>>

    /** The albums of the record label, by name. */
    @Transaction
    @Query(
        """
        SELECT album.* FROM album JOIN album_label al ON al.server_id = album.server_id AND al.album_id = album.id
        WHERE al.server_id = :serverId AND al.label_id = :labelId AND (:all OR EXISTS (
            SELECT 1 FROM track d
            WHERE d.server_id = album.server_id AND d.album_id = album.id AND d.id IN (:downloaded)
        ))
        ORDER BY album.sort_name, album.id
        """,
    )
    fun labelAlbums(
        serverId: String,
        labelId: String,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<AlbumWithArtists>>

    /** The albums tagged with the genre, by name. */
    @Transaction
    @Query(
        """
        SELECT album.* FROM album JOIN album_genre ag ON ag.server_id = album.server_id AND ag.album_id = album.id
        WHERE ag.server_id = :serverId AND ag.genre_id = :genreId AND (:all OR EXISTS (
            SELECT 1 FROM track d
            WHERE d.server_id = album.server_id AND d.album_id = album.id AND d.id IN (:downloaded)
        ))
        ORDER BY album.sort_name, album.id
        """,
    )
    fun genreAlbums(
        serverId: String,
        genreId: String,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<AlbumWithArtists>>

    /** The album artists of the genre's albums, by name. */
    @Query(
        """
        SELECT DISTINCT artist.* FROM artist
        JOIN album_artist aa ON aa.server_id = artist.server_id AND aa.artist_id = artist.id
        JOIN album_genre ag ON ag.server_id = aa.server_id AND ag.album_id = aa.album_id
        WHERE ag.server_id = :serverId AND ag.genre_id = :genreId AND (:all OR EXISTS (
            SELECT 1 FROM track d WHERE d.server_id = aa.server_id AND d.album_id = aa.album_id AND d.id IN (:downloaded)
        ))
        ORDER BY artist.sort_name, artist.id
        """,
    )
    fun genreArtists(
        serverId: String,
        genreId: String,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<ArtistEntity>>

    /** The tracks tagged with the genre, by name. */
    @Transaction
    @Query(
        """
        SELECT track.* FROM track JOIN track_genre tg ON tg.server_id = track.server_id AND tg.track_id = track.id
        WHERE tg.server_id = :serverId AND tg.genre_id = :genreId AND (:all OR track.id IN (:downloaded))
        ORDER BY track.sort_name, track.id
        """,
    )
    fun genreTracks(
        serverId: String,
        genreId: String,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<TrackWithArtists>>

    /** All tracks with their artist credits, from one snapshot. */
    @Transaction
    @Query("SELECT * FROM track WHERE (:all OR track.id IN (:downloaded)) ORDER BY sort_name, id")
    fun tracks(
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<TrackWithArtists>>

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
        SELECT * FROM track WHERE server_id = :serverId AND album_id = :albumId AND (:all OR track.id IN (:downloaded))
        ORDER BY COALESCE(disc_number, 1), track_number IS NULL, track_number, sort_name, id
        """,
    )
    fun albumTracks(
        serverId: String,
        albumId: String,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<TrackWithArtists>>

    /**
     * The genres of the artist's own albums (#133), each once, by name; offline only those of albums holding a
     * downloaded track. The server tags no genres on artists.
     */
    @Query(
        """
        SELECT ag.genre_name FROM album_genre ag
        JOIN album_artist aa ON aa.server_id = ag.server_id AND aa.album_id = ag.album_id
        WHERE ag.server_id = :serverId AND aa.artist_id = :artistId AND (:all OR EXISTS (
            SELECT 1 FROM track d WHERE d.server_id = ag.server_id AND d.album_id = ag.album_id AND d.id IN (:downloaded)
        ))
        GROUP BY ag.genre_id
        ORDER BY ag.genre_name COLLATE NOCASE
        """,
    )
    fun artistGenreNames(
        serverId: String,
        artistId: String,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<String>>

    /** The album's genre names (#132), by name. */
    @Query(
        "SELECT genre_name FROM album_genre WHERE server_id = :serverId AND album_id = :albumId ORDER BY genre_name COLLATE NOCASE",
    )
    fun albumGenreNames(
        serverId: String,
        albumId: String,
    ): Flow<List<String>>

    /** The album's record labels (#132), by name. */
    @Query(
        "SELECT * FROM album_label WHERE server_id = :serverId AND album_id = :albumId ORDER BY label_name COLLATE NOCASE",
    )
    fun albumLabels(
        serverId: String,
        albumId: String,
    ): Flow<List<AlbumLabelEntity>>

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
        ) AND (:all OR EXISTS (
            SELECT 1 FROM track d
            WHERE d.server_id = album.server_id AND d.album_id = album.id AND d.id IN (:downloaded)
        ))
        ORDER BY year IS NULL, year DESC, sort_name, id
        """,
    )
    fun artistAlbums(
        serverId: String,
        artistId: String,
        all: Boolean,
        downloaded: List<String>,
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
        ) AND (:all OR EXISTS (
            SELECT 1 FROM track d
            WHERE d.server_id = album.server_id AND d.album_id = album.id AND d.id IN (:downloaded)
        ))
        ORDER BY year IS NULL, year DESC, sort_name, id
        """,
    )
    fun appearsOnAlbums(
        serverId: String,
        artistId: String,
        all: Boolean,
        downloaded: List<String>,
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
        ) AND (:all OR track.id IN (:downloaded))
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
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<TrackWithArtists>>

    /**
     * Best matches of each kind, from one snapshot: [match] must already be a safe FTS5 expression (see
     * `ftsMatchExpression` in `:core:data`). Ranked by bm25 (shorter names first), then by sort name.
     */
    @Transaction
    suspend fun search(
        match: String,
        limitPerKind: Int,
        all: Boolean,
        downloaded: List<String>,
    ): SearchRows =
        SearchRows(
            artists = searchArtists(match, limitPerKind, all, downloaded),
            albums = searchAlbums(match, limitPerKind, all, downloaded),
            tracks = searchTracks(match, limitPerKind, all, downloaded),
            playlists = searchPlaylists(match, limitPerKind, all, downloaded),
            genreIds = searchGenreIds(match),
            labelIds = searchLabelIds(match),
        )

    /** Record labels whose name matches, best first, like [searchGenreIds]. */
    @Query("SELECT item_id FROM search WHERE search MATCH :match AND kind = 'label' ORDER BY rank, name")
    suspend fun searchLabelIds(match: String): List<String>

    /**
     * Genres whose name matches, best first: their ids only. The genre list (offline-filtered, with counts) supplies the
     * rows, so a genre with nothing offline is dropped there; no limit here, or those could crowd out one that is shown.
     */
    @Query(
        """
        SELECT item_id FROM search WHERE search MATCH :match AND kind = 'genre'
        ORDER BY rank, name
        """,
    )
    suspend fun searchGenreIds(match: String): List<String>

    @Query(
        """
        SELECT artist.* FROM search
        JOIN artist ON artist.server_id = search.server_id AND artist.id = search.item_id
        WHERE search MATCH :match AND search.kind = 'artist' AND (:all OR EXISTS (
            SELECT 1 FROM album_artist aa JOIN track d ON d.server_id = aa.server_id AND d.album_id = aa.album_id
            WHERE aa.server_id = artist.server_id AND aa.artist_id = artist.id AND d.id IN (:downloaded)
        ))
        ORDER BY search.rank, artist.sort_name, artist.id LIMIT :limit
        """,
    )
    suspend fun searchArtists(
        match: String,
        limit: Int,
        all: Boolean,
        downloaded: List<String>,
    ): List<ArtistEntity>

    @Transaction
    @Query(
        """
        SELECT album.* FROM search
        JOIN album ON album.server_id = search.server_id AND album.id = search.item_id
        WHERE search MATCH :match AND search.kind = 'album' AND (:all OR EXISTS (
            SELECT 1 FROM track d
            WHERE d.server_id = album.server_id AND d.album_id = album.id AND d.id IN (:downloaded)
        ))
        ORDER BY search.rank, album.sort_name, album.id LIMIT :limit
        """,
    )
    suspend fun searchAlbums(
        match: String,
        limit: Int,
        all: Boolean,
        downloaded: List<String>,
    ): List<AlbumWithArtists>

    @Transaction
    @Query(
        """
        SELECT track.* FROM search
        JOIN track ON track.server_id = search.server_id AND track.id = search.item_id
        WHERE search MATCH :match AND search.kind = 'track' AND (:all OR track.id IN (:downloaded))
        ORDER BY search.rank, track.sort_name, track.id LIMIT :limit
        """,
    )
    suspend fun searchTracks(
        match: String,
        limit: Int,
        all: Boolean,
        downloaded: List<String>,
    ): List<TrackWithArtists>

    @Query(
        """
        SELECT playlist.* FROM search
        JOIN playlist ON playlist.server_id = search.server_id AND playlist.id = search.item_id
        WHERE search MATCH :match AND search.kind = 'playlist' AND (:all OR EXISTS (
            SELECT 1 FROM playlist_entry e
            WHERE e.server_id = playlist.server_id AND e.playlist_id = playlist.id AND e.track_id IN (:downloaded)
        ))
        ORDER BY search.rank, playlist.sort_name, playlist.id LIMIT :limit
        """,
    )
    suspend fun searchPlaylists(
        match: String,
        limit: Int,
        all: Boolean,
        downloaded: List<String>,
    ): List<PlaylistEntity>

    /**
     * Albums and playlists every track of which is in [downloaded] (the offline overlay, #123); empty ones never count.
     * [downloaded] is bound once (the CTE), so the query stays under SQLite's variable limit like the other offline ones.
     */
    @Query(
        """
        WITH d(id) AS (SELECT id FROM track WHERE id IN (:downloaded))
        SELECT album.server_id, album.id FROM album
        WHERE EXISTS (SELECT 1 FROM track t WHERE t.server_id = album.server_id AND t.album_id = album.id)
        AND NOT EXISTS (
            SELECT 1 FROM track t
            WHERE t.server_id = album.server_id AND t.album_id = album.id AND t.id NOT IN (SELECT id FROM d)
        )
        UNION ALL
        SELECT playlist.server_id, playlist.id FROM playlist
        WHERE EXISTS (
            SELECT 1 FROM playlist_entry e WHERE e.server_id = playlist.server_id AND e.playlist_id = playlist.id
        )
        AND NOT EXISTS (
            SELECT 1 FROM playlist_entry e
            WHERE e.server_id = playlist.server_id AND e.playlist_id = playlist.id AND e.track_id NOT IN (SELECT id FROM d)
        )
        """,
    )
    fun fullyDownloaded(downloaded: List<String>): Flow<List<ItemKey>>
}

/** An item's server and id. */
data class ItemKey(
    @ColumnInfo(name = "server_id") val serverId: String,
    val id: String,
)

/** Search matches grouped by kind, each group best first. */
data class SearchRows(
    val artists: List<ArtistEntity>,
    val albums: List<AlbumWithArtists>,
    val tracks: List<TrackWithArtists>,
    val playlists: List<PlaylistEntity>,
    /** Matching genre ids, best first (#130). */
    val genreIds: List<String> = emptyList(),
    /** Matching record label ids, best first (#131). */
    val labelIds: List<String> = emptyList(),
)

/** A record label with its album count and the cover of its first album (#131). */
data class RecordLabelRow(
    @ColumnInfo(name = "server_id") val serverId: String,
    val id: String,
    val name: String,
    @ColumnInfo(name = "album_count") val albumCount: Int,
    @Embedded val image: ImageColumns,
)

/** A genre with its counts and the cover of its first album (#130). */
data class GenreRow(
    @ColumnInfo(name = "server_id") val serverId: String,
    val id: String,
    val name: String,
    @ColumnInfo(name = "album_count") val albumCount: Int,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @Embedded val image: ImageColumns,
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
    /**
     * The track's album, for its normalization gain; `null` without an album or when it was not synced.
     * shortcut: loads whole album rows (and watches the album table) for one number; store the album gain on the track
     * row at sync if the Songs list ever feels slow.
     */
    @Relation(parentColumns = ["server_id", "album_id"], entityColumns = ["server_id", "id"])
    val album: AlbumEntity? = null,
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
