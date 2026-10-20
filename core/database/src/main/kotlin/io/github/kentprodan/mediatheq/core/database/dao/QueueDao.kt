package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import io.github.kentprodan.mediatheq.core.database.entity.QueueEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.QueueStateEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity

/**
 * The saved play queue. Entries are rewritten only when the queue changes; the state row (current entry, position)
 * changes far more often and is written on its own.
 */
@Dao
interface QueueDao {
    /** The saved entries in queue order, with their tracks; entries whose track left the library are skipped. */
    @Transaction
    @Query(
        """
        SELECT q.position AS queue_position, q.entry_id, q.shuffle_position, t.* FROM queue_entry q
        JOIN track t ON t.server_id = q.server_id AND t.id = q.track_id
        ORDER BY q.position
        """,
    )
    suspend fun entries(): List<QueuedTrack>

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun state(): QueueStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: QueueStateEntity)

    /** Replaces the whole queue and its state at once, so a restore never sees a mix. */
    @Transaction
    suspend fun replace(
        entries: List<QueueEntryEntity>,
        state: QueueStateEntity,
    ) {
        clearEntries()
        insertEntries(entries)
        saveState(state)
    }

    @Transaction
    suspend fun clear() {
        clearEntries()
        clearState()
    }

    @Insert
    suspend fun insertEntries(entries: List<QueueEntryEntity>)

    @Query("DELETE FROM queue_entry")
    suspend fun clearEntries()

    @Query("DELETE FROM queue_state")
    suspend fun clearState()
}

/** A saved queue entry with its track and credits; sort [credits] by position. */
data class QueuedTrack(
    @ColumnInfo(name = "queue_position") val queuePosition: Int,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "shuffle_position") val shufflePosition: Int,
    @Embedded val track: TrackEntity,
    @Relation(
        entity = TrackArtistEntity::class,
        parentColumns = ["server_id", "id"],
        entityColumns = ["server_id", "track_id"],
    )
    val credits: List<TrackCredit>,
)
