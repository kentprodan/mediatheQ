package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.FakeSavedQueueFlag
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DefaultQueueRepositoryTest {
    private val database = inMemoryDatabase()
    private val flag = FakeSavedQueueFlag()
    private val repository = DefaultQueueRepository(database.queueDao(), flag)

    @After
    fun close() = database.close()

    private suspend fun library(vararg ids: String) {
        database.syncDao().stageTracks(ids.map { TrackStaging(TrackEntity("s", it, "Track $it", "Track $it")) })
        database.syncDao().swap(1, "s", "user-1")
    }

    private fun entries(vararg trackIds: String) =
        trackIds.mapIndexed { i, id -> QueueEntryId("e$i", MediaId(ServerId("s"), id)) }

    private fun SavedQueue.trackIds() = entries.map { it.track.id.itemId }

    @Test
    fun `restored tracks carry their album's gain, and none without an album`() =
        runTest {
            database.syncDao().stageAlbums(
                listOf(AlbumStaging(AlbumEntity("s", "al1", "A", "A", normalizationGainDb = -7.5))),
            )
            database.syncDao().stageTracks(
                listOf(
                    TrackStaging(TrackEntity("s", "t1", "Track t1", "Track t1", albumId = "al1")),
                    TrackStaging(TrackEntity("s", "t2", "Track t2", "Track t2")),
                ),
            )
            database.syncDao().swap(1, "s", "user-1")
            repository.saveQueue(entries("t1", "t2"), emptyList(), QueuePlace(0, Duration.ZERO, false))

            val gains = repository.restore()!!.entries.map { it.track.albumNormalizationGainDb }

            assertThat(gains).containsExactly(-7.5, null).inOrder()
        }

    @Test
    fun `each repeat mode is saved and comes back`() =
        runTest {
            library("t1")
            repository.saveQueue(entries("t1"), listOf(0), QueuePlace(0, Duration.ZERO, false))

            RepeatMode.entries.forEach { mode ->
                repository.savePlace(QueuePlace(0, Duration.ZERO, false, mode))
                assertThat(repository.restore()!!.place.repeatMode).isEqualTo(mode)
            }
        }

    @Test
    fun `a saved queue sets the flag, and an empty one or clear unsets it`() =
        runTest {
            library("t1")
            assertThat(repository.hasSavedQueue).isFalse()

            repository.saveQueue(entries("t1"), listOf(0), QueuePlace(0, Duration.ZERO, false))
            assertThat(repository.hasSavedQueue).isTrue()

            repository.saveQueue(emptyList(), emptyList(), QueuePlace(0, Duration.ZERO, false))
            assertThat(repository.hasSavedQueue).isFalse()

            repository.saveQueue(entries("t1"), listOf(0), QueuePlace(0, Duration.ZERO, false))
            repository.clear()
            assertThat(repository.hasSavedQueue).isFalse()
            assertThat(repository.restore()).isNull()
        }

    @Test
    fun `nothing saved restores nothing`() =
        runTest {
            assertThat(repository.restore()).isNull()
        }

    @Test
    fun `a saved queue comes back with its place and shuffle order`() =
        runTest {
            library("t1", "t2", "t3")
            val place = QueuePlace(index = 1, position = 42.seconds, shuffle = true)

            repository.saveQueue(entries("t1", "t2", "t3"), shuffleOrder = listOf(2, 0, 1), place)

            val saved = repository.restore()!!
            assertThat(saved.trackIds()).containsExactly("t1", "t2", "t3").inOrder()
            assertThat(saved.entries.map { it.entryId }).containsExactly("e0", "e1", "e2").inOrder()
            assertThat(saved.shuffleOrder).containsExactly(2, 0, 1).inOrder()
            assertThat(saved.place).isEqualTo(place)
        }

    @Test
    fun `the same track can be queued twice`() =
        runTest {
            library("t1")

            repository.saveQueue(entries("t1", "t1"), listOf(0, 1), QueuePlace(1, Duration.ZERO, false))

            assertThat(repository.restore()!!.trackIds()).containsExactly("t1", "t1")
        }

    @Test
    fun `saving the place keeps the entries`() =
        runTest {
            library("t1", "t2")
            repository.saveQueue(entries("t1", "t2"), listOf(0, 1), QueuePlace(0, Duration.ZERO, false))

            repository.savePlace(QueuePlace(1, 7.seconds, false))

            val saved = repository.restore()!!
            assertThat(saved.trackIds()).containsExactly("t1", "t2").inOrder()
            assertThat(saved.place).isEqualTo(QueuePlace(1, 7.seconds, false))
        }

    @Test
    fun `tracks gone from the library drop out and the place follows`() =
        runTest {
            library("t1", "t3", "t4")
            // t2 (current) was removed by a sync: playback continues with t3, from its start.
            repository.saveQueue(
                entries("t1", "t2", "t3", "t4"),
                shuffleOrder = listOf(3, 1, 0, 2),
                QueuePlace(1, 42.seconds, true),
            )

            val saved = repository.restore()!!
            assertThat(saved.trackIds()).containsExactly("t1", "t3", "t4").inOrder()
            assertThat(saved.place).isEqualTo(QueuePlace(1, Duration.ZERO, true))
            // t4, t1, t3 in play order.
            assertThat(saved.shuffleOrder).containsExactly(2, 0, 1).inOrder()
        }

    @Test
    fun `when every track after the current one is gone the last one left plays`() =
        runTest {
            library("t1")
            repository.saveQueue(entries("t1", "t2"), listOf(0, 1), QueuePlace(1, 5.seconds, false))

            assertThat(repository.restore()!!.place).isEqualTo(QueuePlace(0, Duration.ZERO, false))
        }

    @Test
    fun `nothing left in the library restores nothing`() =
        runTest {
            repository.saveQueue(entries("gone"), listOf(0), QueuePlace(0, Duration.ZERO, false))

            assertThat(repository.restore()).isNull()
        }

    @Test
    fun `an empty queue forgets the saved one`() =
        runTest {
            library("t1")
            repository.saveQueue(entries("t1"), listOf(0), QueuePlace(0, Duration.ZERO, false))

            repository.saveQueue(emptyList(), emptyList(), QueuePlace(0, Duration.ZERO, false))

            assertThat(repository.restore()).isNull()
        }
}
