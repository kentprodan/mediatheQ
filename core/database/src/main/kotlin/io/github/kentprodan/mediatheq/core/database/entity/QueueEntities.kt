package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** One entry of the saved play queue, at [position] in queue order (media3 skill, section 11). */
@Entity(tableName = "queue_entry")
data class QueueEntryEntity(
    @PrimaryKey val position: Int,
    /** Unique per entry, so the same track can be queued twice. */
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    /** Where the entry plays while shuffle is on. */
    @ColumnInfo(name = "shuffle_position") val shufflePosition: Int,
)

/** The single saved player state row: which entry plays, where, whether shuffle is on and how it repeats. */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    /** Queue position of the current entry. */
    @ColumnInfo(name = "current_position") val currentPosition: Int,
    @ColumnInfo(name = "position_ms") val positionMillis: Long,
    val shuffle: Boolean,
    /** 0 off, 1 the whole queue, 2 the current track. */
    @ColumnInfo(name = "repeat_mode", defaultValue = "0") val repeatMode: Int = 0,
)
