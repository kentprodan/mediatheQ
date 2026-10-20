package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.QueueEntryId
import io.github.kentprodan.mediatheq.core.data.QueuePlace
import io.github.kentprodan.mediatheq.core.data.QueueRepository
import io.github.kentprodan.mediatheq.core.data.SavedQueue

/** [QueueRepository] in memory: [saved] is what [restore] returns; [queueWrites] counts full rewrites. */
class FakeQueueRepository(
    var saved: SavedQueue? = null,
) : QueueRepository {
    var entries: List<QueueEntryId> = emptyList()
    var shuffleOrder: List<Int> = emptyList()
    var place: QueuePlace? = null
    var queueWrites = 0

    override suspend fun saveQueue(
        entries: List<QueueEntryId>,
        shuffleOrder: List<Int>,
        place: QueuePlace,
    ) {
        queueWrites++
        this.entries = entries
        this.shuffleOrder = shuffleOrder
        this.place = place.takeIf { entries.isNotEmpty() }
    }

    override suspend fun savePlace(place: QueuePlace) {
        this.place = place
    }

    override suspend fun restore(): SavedQueue? = saved
}
