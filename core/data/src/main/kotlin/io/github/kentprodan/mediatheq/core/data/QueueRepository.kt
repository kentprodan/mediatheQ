package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.time.Duration

/** The saved play queue, restored after the process dies (media3 skill, section 11). */
interface QueueRepository {
    /**
     * Replaces the saved queue. [shuffleOrder] lists queue indices in shuffled play order; an empty [entries] forgets
     * the queue.
     */
    suspend fun saveQueue(
        entries: List<QueueEntryId>,
        shuffleOrder: List<Int>,
        place: QueuePlace,
    )

    /** Updates only where the player is (cheap; the entries stay). */
    suspend fun savePlace(place: QueuePlace)

    /** The saved queue with its tracks, or `null` when none; entries whose track left the library are dropped. */
    suspend fun restore(): SavedQueue?
}

/** One queue entry as saved: its own id (the same track may be queued twice) and its track. */
data class QueueEntryId(
    val entryId: String,
    val trackId: MediaId,
)

/** Where the player is: the current entry's queue index, the position in it, and whether shuffle is on. */
data class QueuePlace(
    val index: Int,
    val position: Duration,
    val shuffle: Boolean,
)

data class SavedQueue(
    val entries: List<SavedEntry>,
    /** Indices into [entries] in shuffled play order. */
    val shuffleOrder: List<Int>,
    val place: QueuePlace,
)

data class SavedEntry(
    val entryId: String,
    val track: Track,
)
