package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.TrackChoiceRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.TrackChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [TrackChoiceRepository] in memory: [choices] by item, season or movie id. */
class FakeTrackChoiceRepository : TrackChoiceRepository {
    val choices = MutableStateFlow<Map<MediaId, TrackChoice>>(emptyMap())

    override fun choice(itemId: MediaId): Flow<TrackChoice?> = choices.map { it[itemId] }

    override suspend fun save(
        itemId: MediaId,
        choice: TrackChoice,
    ) {
        choices.value += itemId to choice
    }

    override suspend fun clear(itemId: MediaId) {
        choices.value -= itemId
    }
}
