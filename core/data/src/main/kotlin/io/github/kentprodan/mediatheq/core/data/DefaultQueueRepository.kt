package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.dao.QueueDao
import io.github.kentprodan.mediatheq.core.database.dao.TrackWithArtists
import io.github.kentprodan.mediatheq.core.database.entity.QueueEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.QueueStateEntity
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class DefaultQueueRepository
    @Inject
    constructor(
        private val queueDao: QueueDao,
    ) : QueueRepository {
        override suspend fun saveQueue(
            entries: List<QueueEntryId>,
            shuffleOrder: List<Int>,
            place: QueuePlace,
        ) {
            if (entries.isEmpty()) return queueDao.clear()
            val shufflePositions = IntArray(entries.size) { it }
            shuffleOrder.forEachIndexed { playPosition, index -> shufflePositions[index] = playPosition }
            queueDao.replace(
                entries.mapIndexed { i, entry ->
                    QueueEntryEntity(
                        i,
                        entry.entryId,
                        entry.trackId.serverId.value,
                        entry.trackId.itemId,
                        shufflePositions[i],
                    )
                },
                place.toEntity(),
            )
        }

        override suspend fun savePlace(place: QueuePlace) = queueDao.saveState(place.toEntity())

        override suspend fun restore(): SavedQueue? {
            val state = queueDao.state() ?: return null
            val rows = queueDao.entries().ifEmpty { return null }
            // The current entry, or the next one left when its track is gone (then from its start).
            val index =
                rows.indexOfFirst { it.queuePosition >= state.currentPosition }.takeIf { it >= 0 } ?: rows.lastIndex
            val sameEntry = rows[index].queuePosition == state.currentPosition
            return SavedQueue(
                entries =
                    rows.map {
                        SavedEntry(
                            it.entryId,
                            TrackWithArtists(it.track, it.credits, it.album).toDomain(),
                        )
                    },
                shuffleOrder = rows.indices.sortedBy { rows[it].shufflePosition },
                place =
                    QueuePlace(
                        index = index,
                        position = if (sameEntry) state.positionMillis.milliseconds else Duration.ZERO,
                        shuffle = state.shuffle,
                    ),
            )
        }

        private fun QueuePlace.toEntity() = QueueStateEntity(0, index, position.inWholeMilliseconds, shuffle)
    }
