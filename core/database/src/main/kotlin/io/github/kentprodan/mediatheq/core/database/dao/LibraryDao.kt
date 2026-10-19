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
import io.github.kentprodan.mediatheq.core.database.entity.SearchEntity
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

    /** One album with its credits, `null` when it is not in the library. */
    @Transaction
    @Query("SELECT * FROM album WHERE server_id = :serverId AND id = :albumId")
    fun album(
        serverId: String,
        albumId: String,
    ): Flow<AlbumWithArtists?>

    /**
     * The tracks of one album in play order: disc (unknown = 1), then track number (unknown last), then name.
     * shortcut: no index on track.album_id (it would need a migration); a scan of < 10k rows is fast. Add one with the
     * first schema change.
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

    /** [match] must already be a safe FTS5 expression (see `ftsMatchExpression` in `:core:data`). */
    @Query("SELECT * FROM search WHERE search MATCH :match ORDER BY rank LIMIT :limit")
    suspend fun search(
        match: String,
        limit: Int,
    ): List<SearchEntity>
}

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
