package io.github.kentprodan.mediatheq.core.database

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumLabelEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumLabelStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.ImageColumns
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/** Record labels (#131). al1 "Zebra" (Atlantic, Electrecord) holds t1; al2 "Apple" (Atlantic) holds t2. */
class RecordLabelQueriesTest {
    private val database = inMemoryDatabase()
    private val libraryDao = database.libraryDao()
    private val syncDao = database.syncDao()

    @After
    fun close() = database.close()

    private suspend fun sync(atlanticName: String = "Atlantic") {
        syncDao.stageAlbums(
            listOf(
                AlbumEntity("s", "al1", "Zebra", "Zebra", image = ImageColumns("al1", "tag1", "hash1")),
                AlbumEntity("s", "al2", "Apple", "Apple", image = ImageColumns("al2", "tag2", "hash2")),
            ).map(::AlbumStaging),
        )
        syncDao.stageTracks(
            listOf(
                TrackEntity("s", "t1", "One", "One", albumId = "al1"),
                TrackEntity("s", "t2", "Two", "Two", albumId = "al2"),
            ).map(::TrackStaging),
        )
        syncDao.stageAlbumLabelLinks(
            listOf(
                AlbumLabelEntity("s", "al1", "l-atl", atlanticName),
                AlbumLabelEntity("s", "al1", "l-ele", "Electrecord"),
                AlbumLabelEntity("s", "al2", "l-atl", atlanticName),
            ).map(::AlbumLabelStaging),
        )
        syncDao.swap(1, "s", "user-1")
    }

    @Test
    fun `labels come from their albums with album counts and the first album's cover`() =
        runTest {
            sync()

            val labels = libraryDao.recordLabels(true, emptyList()).first()

            assertThat(labels.map { it.name }).containsExactly("Atlantic", "Electrecord").inOrder()
            val atlantic = labels.first()
            assertThat(atlantic.albumCount).isEqualTo(2)
            // Apple comes before Zebra by name: its cover is the label's.
            assertThat(atlantic.image).isEqualTo(ImageColumns("al2", "tag2", "hash2"))
            assertThat(libraryDao.labelAlbums("s", "l-atl", true, emptyList()).first().map { it.album.id })
                .containsExactly("al2", "al1")
                .inOrder()
        }

    @Test
    fun `offline a label counts only albums holding a download and one with none is left out`() =
        runTest {
            sync()
            val downloaded = listOf("t2")

            val labels = libraryDao.recordLabels(false, downloaded).first()

            assertThat(labels.map { it.name }).containsExactly("Atlantic")
            assertThat(labels.single().albumCount).isEqualTo(1)
            assertThat(libraryDao.labelAlbums("s", "l-atl", false, downloaded).first().map { it.album.id })
                .containsExactly("al2")
            assertThat(libraryDao.labelAlbums("s", "l-ele", false, downloaded).first()).isEmpty()
        }

    @Test
    fun `a label renamed on the server shows its new name after the next sync`() =
        runTest {
            sync()
            sync(atlanticName = "Atlantic Records")

            assertThat(libraryDao.recordLabels(true, emptyList()).first().map { it.name })
                .containsExactly("Atlantic Records", "Electrecord")
                .inOrder()
        }

    @Test
    fun `search finds labels without diacritics`() =
        runTest {
            syncDao.stageAlbums(listOf(AlbumStaging(AlbumEntity("s", "al1", "Zebra", "Zebra"))))
            syncDao.stageAlbumLabelLinks(listOf(AlbumLabelStaging(AlbumLabelEntity("s", "al1", "l-ro", "Casa Română"))))
            syncDao.swap(1, "s", "user-1")

            assertThat(libraryDao.search("romana*", 10, true, emptyList()).labelIds).containsExactly("l-ro")
        }

    @Test
    fun `sign-out forgets the labels`() =
        runTest {
            sync()

            syncDao.clearAll()

            assertThat(libraryDao.recordLabels(true, emptyList()).first()).isEmpty()
        }
}
