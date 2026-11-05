package io.github.kentprodan.mediatheq.core.database

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumGenreEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumGenreStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumLabelEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumLabelStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumResumeEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/** The album page (#132): its overview, release date, file sizes, genres, labels and where it was left. */
class AlbumPageQueriesTest {
    private val database = inMemoryDatabase()
    private val libraryDao = database.libraryDao()
    private val syncDao = database.syncDao()
    private val resumeDao = database.albumResumeDao()

    @After
    fun close() = database.close()

    private suspend fun sync() {
        syncDao.stageAlbums(
            listOf(
                AlbumStaging(
                    AlbumEntity("s", "al1", "Satelit", "Satelit", overview = "About it.", releaseDateMillis = 1_000),
                ),
            ),
        )
        syncDao.stageTracks(listOf(TrackStaging(TrackEntity("s", "t1", "One", "One", albumId = "al1", sizeBytes = 42))))
        syncDao.stageAlbumGenreLinks(
            listOf(AlbumGenreEntity("s", "al1", "g2", "rock"), AlbumGenreEntity("s", "al1", "g1", "Folk"))
                .map(::AlbumGenreStaging),
        )
        syncDao.stageAlbumLabelLinks(listOf(AlbumLabelStaging(AlbumLabelEntity("s", "al1", "l1", "Electrecord"))))
        syncDao.swap(1, "s", "user-1")
    }

    @Test
    fun `the album keeps its overview and release date, and its tracks their file size`() =
        runTest {
            sync()

            val album = libraryDao.album("s", "al1").first()!!.album
            assertThat(album.overview).isEqualTo("About it.")
            assertThat(album.releaseDateMillis).isEqualTo(1_000)
            assertThat(
                libraryDao
                    .albumTracks("s", "al1", true, emptyList())
                    .first()
                    .single()
                    .track.sizeBytes,
            ).isEqualTo(42)
        }

    @Test
    fun `the album's genres and labels come by name`() =
        runTest {
            sync()

            assertThat(libraryDao.albumGenreNames("s", "al1").first()).containsExactly("Folk", "rock").inOrder()
            assertThat(libraryDao.albumLabels("s", "al1").first().map { it.labelName }).containsExactly("Electrecord")
        }

    @Test
    fun `where an album was left survives a full sync and is forgotten on sign-out`() =
        runTest {
            sync()
            resumeDao.save(AlbumResumeEntity("s", "al1", "t1", 30_000))
            resumeDao.save(AlbumResumeEntity("s", "al1", "t1", 60_000))

            sync()
            assertThat(resumeDao.resume("s", "al1").first()).isEqualTo(AlbumResumeEntity("s", "al1", "t1", 60_000))
            assertThat(resumeDao.albumOf("s", "t1")).isEqualTo("al1")
            assertThat(resumeDao.albumOf("s", "gone")).isNull()

            syncDao.clearAll()
            assertThat(resumeDao.resume("s", "al1").first()).isNull()
        }
}
