package io.github.kentprodan.mediatheq.core.database

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
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
import org.junit.Before
import org.junit.Test

/** The downloaded-only reads of offline mode (#63): only t2 is on the device. */
class OfflineQueriesTest {
    private val database = inMemoryDatabase()
    private val libraryDao = database.libraryDao()
    private val playlistDao = database.playlistDao()
    private val downloaded = listOf("t2")

    @After
    fun close() = database.close()

    // ar1 owns al1 (t1, t2) and al2 (t3); ar2 owns al3 (t4); ar3 is credit-only, featured on t2.
    // Playlist p1 holds t1 and t2, p2 holds t4.
    @Before
    fun library() =
        runTest {
            val syncDao = database.syncDao()
            syncDao.stageAlbumArtists(listOf(artist("ar1"), artist("ar2")).map(::ArtistStaging))
            syncDao.stageCreditedArtists(listOf(ArtistStaging(artist("ar3").copy(isAlbumArtist = false))))
            syncDao.stageAlbums(listOf("al1", "al2", "al3").map { AlbumStaging(AlbumEntity("s", it, it, it)) })
            syncDao.stageAlbumArtistLinks(
                listOf("al1" to "ar1", "al2" to "ar1", "al3" to "ar2").map { (album, artist) ->
                    AlbumArtistStaging(AlbumArtistEntity("s", album, 0, artist))
                },
            )
            syncDao.stageTracks(
                listOf(
                    track("t1", "al1", "Intro"),
                    track("t2", "al1", "Hora Moșoi"),
                    track("t3", "al2", "Moșoi lung"),
                    track("t4", "al3", "Outro"),
                ).map(::TrackStaging),
            )
            syncDao.stageTrackArtistLinks(
                listOf(
                    TrackArtistEntity("s", "t1", 0, "ar1"),
                    TrackArtistEntity("s", "t2", 0, "ar1"),
                    TrackArtistEntity("s", "t2", 1, "ar3"),
                    TrackArtistEntity("s", "t3", 0, "ar1"),
                    TrackArtistEntity("s", "t4", 0, "ar2"),
                ).map(::TrackArtistStaging),
            )
            syncDao.swap(1, "s", "user-1")
            playlistDao.replace(playlist("p1"), entries("p1", "t1", "t2"))
            playlistDao.replace(playlist("p2"), entries("p2", "t4"))
        }

    private fun artist(id: String) = ArtistEntity("s", id, id, id, isAlbumArtist = true)

    private fun track(
        id: String,
        albumId: String,
        name: String,
    ) = TrackEntity("s", id, name, name, albumId = albumId)

    private fun playlist(id: String) = PlaylistEntity("s", id, id, id)

    private fun entries(
        playlistId: String,
        vararg trackIds: String,
    ) = trackIds.mapIndexed { position, id -> PlaylistEntryEntity("s", playlistId, position, "$playlistId-$id", id) }

    @Test
    fun `lists keep only downloaded tracks and what holds them`() =
        runTest {
            assertThat(libraryDao.tracks(false, downloaded).first().map { it.track.id }).containsExactly("t2")
            assertThat(libraryDao.albums(false, downloaded).first().map { it.album.id }).containsExactly("al1")
            assertThat(libraryDao.albumArtists(false, downloaded).first().map { it.id }).containsExactly("ar1")
            assertThat(libraryDao.playlists(false, downloaded).first().map { it.id }).containsExactly("p1")
        }

    @Test
    fun `album, artist and playlist pages keep only what is downloaded`() =
        runTest {
            assertThat(libraryDao.albumTracks("s", "al1", false, downloaded).first().map { it.track.id })
                .containsExactly("t2")
            assertThat(libraryDao.artistAlbums("s", "ar1", false, downloaded).first().map { it.album.id })
                .containsExactly("al1")
            assertThat(libraryDao.appearsOnAlbums("s", "ar3", false, downloaded).first().map { it.album.id })
                .containsExactly("al1")
            assertThat(libraryDao.artistTracks("s", "ar1", false, downloaded).first().map { it.track.id })
                .containsExactly("t2")
            assertThat(playlistDao.tracks("s", "p1", false, downloaded).first().map { it.track.id })
                .containsExactly("t2")
            assertThat(playlistDao.tracks("s", "p2", false, downloaded).first()).isEmpty()
        }

    @Test
    fun `search finds only downloaded items, also without diacritics`() =
        runTest {
            val rows = libraryDao.search("mosoi*", 10, false, downloaded)

            assertThat(rows.tracks.map { it.track.id }).containsExactly("t2")
            assertThat(libraryDao.search("ar1*", 10, false, downloaded).artists.map { it.id }).containsExactly("ar1")
            assertThat(libraryDao.search("ar2*", 10, false, downloaded).artists).isEmpty()
            assertThat(libraryDao.search("al*", 10, false, downloaded).albums.map { it.album.id })
                .containsExactly("al1")
            assertThat(libraryDao.search("p*", 10, false, downloaded).playlists.map { it.id }).containsExactly("p1")
        }

    @Test
    fun `nothing downloaded lists nothing, and all lists everything`() =
        runTest {
            assertThat(libraryDao.albums(false, emptyList()).first()).isEmpty()
            assertThat(libraryDao.albums(true, emptyList()).first()).hasSize(3)
        }

    @Test
    fun `ten thousand downloaded ids still fit in one query`() =
        runTest {
            val many = List(10_000) { "x$it" } + "t2"

            assertThat(libraryDao.tracks(false, many).first().map { it.track.id }).containsExactly("t2")
        }
}
