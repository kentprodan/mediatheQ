package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.kentprodan.mediatheq.core.database.entity.WaveformEntity
import kotlinx.coroutines.flow.Flow

/** Waveforms computed on the device (#124). */
@Dao
interface WaveformDao {
    @Query("SELECT * FROM waveform WHERE server_id = :serverId AND item_id = :itemId")
    fun waveform(
        serverId: String,
        itemId: String,
    ): Flow<WaveformEntity?>

    /** The file size the stored waveform was computed from, or `null` when none is stored. */
    @Query("SELECT size_bytes FROM waveform WHERE server_id = :serverId AND item_id = :itemId")
    suspend fun sizeOf(
        serverId: String,
        itemId: String,
    ): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(waveform: WaveformEntity)
}
