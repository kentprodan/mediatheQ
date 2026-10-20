package io.github.kentprodan.mediatheq.core.database

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.QueueEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.QueueStateEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class QueueDaoTest {
    private val database = inMemoryDatabase()
    private val queueDao = database.queueDao()
    private val syncDao = database.syncDao()

    @After
    fun close() = database.close()

    private suspend fun library(vararg ids: String) {
        syncDao.stageAlbumArtists(
            listOf(ArtistStaging(ArtistEntity("s", "ar1", "Subcarpați", "Subcarpati", isAlbumArtist = true))),
        )
        syncDao.stageTracks(ids.map { TrackStaging(TrackEntity("s", it, "Track $it", "Track $it")) })
        syncDao.stageTrackArtistLinks(ids.map { TrackArtistStaging(TrackArtistEntity("s", it, 0, "ar1")) })
        syncDao.swap(1, "s", "user-1")
    }

    private fun entry(
        position: Int,
        trackId: String,
        shufflePosition: Int = position,
    ) = QueueEntryEntity(position, "e$position", "s", trackId, shufflePosition)

    private val state = QueueStateEntity(currentPosition = 1, positionMillis = 42_000, shuffle = true)

    @Test
    fun `entries come back in queue order with their tracks and credits`() =
        runTest {
            library("t1", "t2")
            queueDao.replace(listOf(entry(0, "t2", 1), entry(1, "t1", 0), entry(2, "t2", 2)), state)

            val entries = queueDao.entries()

            assertThat(entries.map { it.entryId }).containsExactly("e0", "e1", "e2").inOrder()
            assertThat(entries.map { it.track.id }).containsExactly("t2", "t1", "t2").inOrder()
            assertThat(entries.map { it.shufflePosition }).containsExactly(1, 0, 2).inOrder()
            assertThat(
                entries
                    .first()
                    .credits
                    .single()
                    .artist
                    ?.name,
            ).isEqualTo("Subcarpați")
            assertThat(queueDao.state()).isEqualTo(state)
        }

    @Test
    fun `entries whose track left the library are skipped`() =
        runTest {
            library("t1")
            queueDao.replace(listOf(entry(0, "gone"), entry(1, "t1")), state)

            assertThat(queueDao.entries().map { it.queuePosition }).containsExactly(1)
        }

    @Test
    fun `replace drops the previous queue`() =
        runTest {
            library("t1", "t2")
            queueDao.replace(listOf(entry(0, "t1"), entry(1, "t2")), state)

            queueDao.replace(listOf(entry(0, "t2")), state.copy(currentPosition = 0))

            assertThat(queueDao.entries().map { it.track.id }).containsExactly("t2")
            assertThat(queueDao.state()?.currentPosition).isEqualTo(0)
        }

    @Test
    fun `signing out forgets the saved queue`() =
        runTest {
            library("t1")
            queueDao.replace(listOf(entry(0, "t1")), state)

            syncDao.clearAll()

            assertThat(queueDao.entries()).isEmpty()
            assertThat(queueDao.state()).isNull()
        }
}
