package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.kentprodan.mediatheq.core.database.entity.AlbumResumeEntity
import kotlinx.coroutines.flow.Flow

/** Where listening to each album stopped (#132). */
@Dao
interface AlbumResumeDao {
    @Query("SELECT * FROM album_resume WHERE server_id = :serverId AND album_id = :albumId")
    fun resume(
        serverId: String,
        albumId: String,
    ): Flow<AlbumResumeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(resume: AlbumResumeEntity)

    /** The album of [trackId], or `null` when the track has none or is not in the library. */
    @Query("SELECT album_id FROM track WHERE server_id = :serverId AND id = :trackId")
    suspend fun albumOf(
        serverId: String,
        trackId: String,
    ): String?
}
