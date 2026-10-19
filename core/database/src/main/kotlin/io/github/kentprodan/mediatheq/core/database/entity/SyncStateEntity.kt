package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * The single sync bookkeeping row. The checkpoint fields describe a sync in progress whose pages sit in the staging
 * tables; they are `null` when no sync is pending. Lives in the database so it commits atomically with the swap.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "last_full_sync_at") val lastFullSyncAtMillis: Long? = null,
    @ColumnInfo(name = "checkpoint_server_id") val checkpointServerId: String? = null,
    @ColumnInfo(name = "checkpoint_user_id") val checkpointUserId: String? = null,
    @ColumnInfo(name = "checkpoint_started_at") val checkpointStartedAtMillis: Long? = null,
    /** Name of the sync step to continue with. */
    @ColumnInfo(name = "checkpoint_step") val checkpointStep: String? = null,
    /** Paging offset within [checkpointStep]; for playlist entries, the index of the next playlist. */
    @ColumnInfo(name = "checkpoint_index") val checkpointIndex: Int = 0,
)
