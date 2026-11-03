package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.WaveformRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Waveforms in memory: the size each one was analyzed from, and its levels. */
class FakeWaveformRepository : WaveformRepository {
    val stored = MutableStateFlow<Map<MediaId, Pair<Long, List<Float>?>>>(emptyMap())

    override fun levels(id: MediaId) = stored.map { it[id]?.second }

    override suspend fun isAnalyzed(
        id: MediaId,
        sizeBytes: Long,
    ) = stored.value[id]?.first == sizeBytes

    override suspend fun save(
        id: MediaId,
        sizeBytes: Long,
        levels: List<Float>?,
    ) = stored.update { it + (id to (sizeBytes to levels)) }
}
