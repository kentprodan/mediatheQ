package io.github.kentprodan.mediatheq.core.database

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class PlaylistDaoTest {
    private val database = inMemoryDatabase()
    private val playlistDao = database.playlistDao()
    private val libraryDao = database.libraryDao()
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

    private val playlist = PlaylistEntity("s", "p1", "Drum lung", "Drum lung", trackCount = 3)

    private fun entries(vararg trackIds: String) =
        trackIds.mapIndexed { position, id -> PlaylistEntryEntity("s", "p1", position, id, id) }

    @Test
    fun `tracks come in playlist order with credits and skip tracks not in the library`() =
        runTest {
            library("t1", "t2")

            playlistDao.replace(playlist, entries("t2", "gone", "t1"))

            val tracks = playlistDao.tracks("s", "p1").first()
            assertThat(tracks.map { it.track.id to it.position }).containsExactly("t2" to 0, "t1" to 2).inOrder()
            assertThat(
                tracks
                    .first()
                    .credits
                    .single()
                    .artist
                    ?.name,
            ).isEqualTo("Subcarpați")
            assertThat(playlistDao.entryRows("s", "p1").map { it.trackId }).containsExactly("t2", "gone", "t1")
        }

    @Test
    fun `replace swaps the row and every entry at once`() =
        runTest {
            library("t1", "t2", "t3")
            playlistDao.replace(playlist, entries("t1", "t2", "t3"))

            playlistDao.tracks("s", "p1").test {
                assertThat(awaitItem().map { it.track.id }).containsExactly("t1", "t2", "t3").inOrder()

                playlistDao.replace(playlist.copy(name = "Nou", trackCount = 2), entries("t3", "t1"))

                assertThat(awaitItem().map { it.track.id }).containsExactly("t3", "t1").inOrder()
            }
            assertThat(playlistDao.playlistRow("s", "p1")?.trackCount).isEqualTo(2)
        }

    @Test
    fun `a renamed playlist is found by its new name only`() =
        runTest {
            playlistDao.replace(playlist, emptyList())

            playlistDao.replace(playlist.copy(name = "Seară târzie"), emptyList())

            assertThat(libraryDao.search("seara", 10).playlists.map { it.id }).containsExactly("p1")
            assertThat(libraryDao.search("drum", 10).playlists).isEmpty()
        }

    @Test
    fun `delete forgets the playlist, its entries and its search row`() =
        runTest {
            library("t1")
            playlistDao.replace(playlist, entries("t1"))

            playlistDao.delete("s", "p1")

            assertThat(playlistDao.playlist("s", "p1").first()).isNull()
            assertThat(playlistDao.entryRows("s", "p1")).isEmpty()
            assertThat(libraryDao.search("drum", 10).playlists).isEmpty()
            assertThat(libraryDao.playlists().first()).isEmpty()
        }
}
