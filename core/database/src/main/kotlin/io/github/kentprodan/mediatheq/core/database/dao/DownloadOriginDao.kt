package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.kentprodan.mediatheq.core.database.entity.DownloadOriginEntity
import kotlinx.coroutines.flow.Flow

/** What each download was started from (#168). */
@Dao
interface DownloadOriginDao {
    @Query("SELECT * FROM download_origin")
    fun all(): Flow<List<DownloadOriginEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(origins: List<DownloadOriginEntity>)

    /** Forgets one collection; its tracks stay for the other collections that keep them. */
    @Query("DELETE FROM download_origin WHERE kind = :kind AND server_id = :serverId AND collection_id = :collectionId")
    suspend fun removeCollection(
        kind: String,
        serverId: String,
        collectionId: String,
    )

    /** Forgets one origin of one track. */
    @Query(
        "DELETE FROM download_origin WHERE server_id = :serverId AND track_id = :trackId AND kind = :kind " +
            "AND collection_id = :collectionId",
    )
    suspend fun removeOrigin(
        serverId: String,
        trackId: String,
        kind: String,
        collectionId: String,
    )

    /** Forgets every origin of these tracks of [serverId] (their downloads are gone). */
    @Query("DELETE FROM download_origin WHERE server_id = :serverId AND track_id IN (:trackIds)")
    suspend fun removeTracks(
        serverId: String,
        trackIds: List<String>,
    )
}
