package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.dao.WaveformDao
import io.github.kentprodan.mediatheq.core.database.entity.WaveformEntity
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.math.roundToInt

/** Waveforms computed on the device from a track's file (#124, ADR 0027). */
interface WaveformRepository {
    /** The levels (0..1, in play order) of [id]; `null` while none is known or its file cannot be decoded here. */
    fun levels(id: MediaId): Flow<List<Float>?>

    /** True when the file of [sizeBytes] was already analyzed (with or without a result): no need to decode it again. */
    suspend fun isAnalyzed(
        id: MediaId,
        sizeBytes: Long,
    ): Boolean

    /** Keeps the result for the file of [sizeBytes]; `null` [levels] = no decoder for its format on this device. */
    suspend fun save(
        id: MediaId,
        sizeBytes: Long,
        levels: List<Float>?,
    )
}

internal class DefaultWaveformRepository
    @Inject
    constructor(
        private val dao: WaveformDao,
    ) : WaveformRepository {
        override fun levels(id: MediaId): Flow<List<Float>?> =
            dao.waveform(id.serverId.value, id.itemId).map { row ->
                row?.levels?.map { (it.toInt() and BYTE_MASK) / LEVEL_MAX }
            }

        override suspend fun isAnalyzed(
            id: MediaId,
            sizeBytes: Long,
        ): Boolean = dao.sizeOf(id.serverId.value, id.itemId) == sizeBytes

        override suspend fun save(
            id: MediaId,
            sizeBytes: Long,
            levels: List<Float>?,
        ) {
            val bytes =
                levels?.let { list ->
                    ByteArray(list.size) { (list[it].coerceIn(0f, 1f) * LEVEL_MAX).roundToInt().toByte() }
                }
            dao.save(WaveformEntity(id.serverId.value, id.itemId, sizeBytes, bytes))
        }

        private companion object {
            const val BYTE_MASK = 0xFF
            const val LEVEL_MAX = 255f
        }
    }
