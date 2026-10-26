package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.SearchEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * One playlist at a time, for the playlist screen and its edits. Every write replaces the playlist row, all its entries
 * and its search row in one transaction, so an edit and its revert are the same operation.
 */
@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist WHERE server_id = :serverId AND id = :playlistId")
    fun playlist(
        serverId: String,
        playlistId: String,
    ): Flow<PlaylistEntity?>

    /**
     * The entries in playlist order with their tracks; entries whose track is not in the library are skipped, and
     * with `all` false those not in `downloaded` too (see [LibraryDao]).
     */
    @Transaction
    @Query(
        """
        SELECT e.position AS entry_position, e.entry_id, t.* FROM playlist_entry e
        JOIN track t ON t.server_id = e.server_id AND t.id = e.track_id
        WHERE e.server_id = :serverId AND e.playlist_id = :playlistId AND (:all OR t.id IN (:downloaded))
        ORDER BY e.position
        """,
    )
    fun tracks(
        serverId: String,
        playlistId: String,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<PlaylistTrack>>

    @Query("SELECT * FROM playlist WHERE server_id = :serverId AND id = :playlistId")
    suspend fun playlistRow(
        serverId: String,
        playlistId: String,
    ): PlaylistEntity?

    /** All entries, hidden ones included, by position: what the server counts. */
    @Query("SELECT * FROM playlist_entry WHERE server_id = :serverId AND playlist_id = :playlistId ORDER BY position")
    suspend fun entryRows(
        serverId: String,
        playlistId: String,
    ): List<PlaylistEntryEntity>

    /** Writes [playlist] with exactly [entries] (positions as given) and indexes its name for search. */
    @Transaction
    suspend fun replace(
        playlist: PlaylistEntity,
        entries: List<PlaylistEntryEntity>,
    ) {
        delete(playlist.serverId, playlist.id)
        insertPlaylist(playlist)
        insertEntries(entries)
        insertSearch(SearchEntity("playlist", playlist.serverId, playlist.id, playlist.name))
    }

    /** [replace], then counts entries and sums the known tracks' durations into the row, for an edit not yet synced. */
    @Transaction
    suspend fun replaceAndCount(
        playlist: PlaylistEntity,
        entries: List<PlaylistEntryEntity>,
    ) {
        replace(playlist, entries)
        recount(playlist.serverId, playlist.id)
    }

    @Query(
        """
        UPDATE playlist SET
            track_count = (SELECT COUNT(*) FROM playlist_entry WHERE server_id = :serverId AND playlist_id = :playlistId),
            duration_ms = (
                SELECT COALESCE(SUM(t.duration_ms), 0) FROM playlist_entry e
                JOIN track t ON t.server_id = e.server_id AND t.id = e.track_id
                WHERE e.server_id = :serverId AND e.playlist_id = :playlistId
            )
        WHERE server_id = :serverId AND id = :playlistId
        """,
    )
    suspend fun recount(
        serverId: String,
        playlistId: String,
    )

    /** Forgets the playlist, its entries and its search row. */
    @Transaction
    suspend fun delete(
        serverId: String,
        playlistId: String,
    ) {
        deletePlaylist(serverId, playlistId)
        deleteEntries(serverId, playlistId)
        deleteSearch(serverId, playlistId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Insert
    suspend fun insertSearch(row: SearchEntity)

    @Query("DELETE FROM playlist WHERE server_id = :serverId AND id = :playlistId")
    suspend fun deletePlaylist(
        serverId: String,
        playlistId: String,
    )

    @Query("DELETE FROM playlist_entry WHERE server_id = :serverId AND playlist_id = :playlistId")
    suspend fun deleteEntries(
        serverId: String,
        playlistId: String,
    )

    @Query("DELETE FROM search WHERE kind = 'playlist' AND server_id = :serverId AND item_id = :playlistId")
    suspend fun deleteSearch(
        serverId: String,
        playlistId: String,
    )
}

/** A playlist entry with its track and credits; sort [credits] by position. */
data class PlaylistTrack(
    @ColumnInfo(name = "entry_position") val position: Int,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @Embedded val track: TrackEntity,
    @Relation(
        entity = TrackArtistEntity::class,
        parentColumns = ["server_id", "id"],
        entityColumns = ["server_id", "track_id"],
    )
    val credits: List<TrackCredit>,
    /** The track's album, for its normalization gain; `null` without an album or when it was not synced. */
    @Relation(parentColumns = ["server_id", "album_id"], entityColumns = ["server_id", "id"])
    val album: AlbumEntity? = null,
)
