package io.github.kentprodan.mediatheq.core.database

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/**
 * R16: an artist with folders in two libraries is two server items; the album artist list has one of them, albums may
 * credit the other. The sync keeps the listed one.
 */
class SameNameArtistsTest {
    private val database = inMemoryDatabase()
    private val syncDao = database.syncDao()
    private val libraryDao = database.libraryDao()

    @After
    fun close() = database.close()

    private fun artist(
        id: String,
        name: String,
        albumArtist: Boolean,
    ) = ArtistStaging(ArtistEntity("s", id, name, name, isAlbumArtist = albumArtist))

    /** Stages [albumArtists] as the server lists them, [credited] as album and track credits only, then swaps. */
    private suspend fun sync(
        albumArtists: List<ArtistStaging>,
        credited: List<ArtistStaging>,
        albumCredits: List<String>,
        trackCredits: List<String> = albumCredits,
    ) {
        syncDao.stageAlbumArtists(albumArtists)
        syncDao.stageCreditedArtists(credited)
        syncDao.stageAlbums(listOf(AlbumStaging(AlbumEntity("s", "al", "+373", "+373"))))
        syncDao.stageAlbumArtistLinks(
            albumCredits.mapIndexed { i, id -> AlbumArtistStaging(AlbumArtistEntity("s", "al", i, id)) },
        )
        syncDao.stageTracks(listOf(TrackStaging(TrackEntity("s", "t", "Song", "Song", albumId = "al"))))
        syncDao.stageTrackArtistLinks(
            trackCredits.mapIndexed { i, id -> TrackArtistStaging(TrackArtistEntity("s", "t", i, id)) },
        )
        syncDao.swap(1, "s", "user-1")
    }

    private suspend fun satoshi() =
        sync(
            albumArtists = listOf(artist("listed", "Satoshi", albumArtist = true)),
            credited = listOf(artist("other", "Satoshi", albumArtist = false)),
            albumCredits = listOf("other"),
        )

    @Test
    fun `R16 an album crediting the unlisted namesake belongs to the listed album artist`() =
        runTest {
            satoshi()

            assertThat(libraryDao.albumArtists(true, emptyList()).first().map { it.id }).containsExactly("listed")
            assertThat(libraryDao.artistAlbums("s", "listed", true, emptyList()).first().map { it.album.id })
                .containsExactly("al")
        }

    @Test
    fun `R16 the track credit leads to the listed album artist too`() =
        runTest {
            satoshi()

            assertThat(libraryDao.artistTracks("s", "listed", true, emptyList()).first().map { it.track.id })
                .containsExactly("t")
        }

    @Test
    fun `R16 offline the listed album artist shows with a downloaded track, and search finds him`() =
        runTest {
            satoshi()

            assertThat(libraryDao.albumArtists(false, listOf("t")).first().map { it.id }).containsExactly("listed")
            assertThat(libraryDao.search("satoshi*", 10, false, listOf("t")).artists.map { it.id })
                .containsExactly("listed")
        }

    @Test
    fun `R16 an album crediting both namesakes credits him once`() =
        runTest {
            sync(
                albumArtists = listOf(artist("listed", "Satoshi", albumArtist = true)),
                credited = listOf(artist("other", "Satoshi", albumArtist = false)),
                albumCredits = listOf("listed", "other"),
            )

            val album = libraryDao.artistAlbums("s", "listed", true, emptyList()).first().single()
            assertThat(album.credits.map { it.link.artistId }).containsExactly("listed")
            val track = libraryDao.artistTracks("s", "listed", true, emptyList()).first().single()
            assertThat(track.credits.map { it.link.artistId }).containsExactly("listed")
        }

    @Test
    fun `R16 names match without regard to case`() =
        runTest {
            sync(
                albumArtists = listOf(artist("listed", "Satoshi", albumArtist = true)),
                credited = listOf(artist("other", "SATOSHI", albumArtist = false)),
                albumCredits = listOf("other"),
            )

            assertThat(libraryDao.artistAlbums("s", "listed", true, emptyList()).first()).hasSize(1)
        }

    @Test
    fun `an album artist missing from the list, with no namesake, becomes an album artist`() =
        runTest {
            sync(
                albumArtists = emptyList(),
                credited = listOf(artist("lone", "Etherwood", albumArtist = false)),
                albumCredits = listOf("lone"),
            )

            assertThat(libraryDao.albumArtists(true, emptyList()).first().map { it.id }).containsExactly("lone")
        }

    @Test
    fun `a featured artist stays credit-only`() =
        runTest {
            sync(
                albumArtists = listOf(artist("main", "Avicii", albumArtist = true)),
                credited = listOf(artist("feat", "Aloe Blacc", albumArtist = false)),
                albumCredits = listOf("main"),
                trackCredits = listOf("main", "feat"),
            )

            assertThat(libraryDao.albumArtists(true, emptyList()).first().map { it.id }).containsExactly("main")
            val track = libraryDao.artistTracks("s", "main", true, emptyList()).first().single()
            assertThat(track.credits.map { it.link.artistId }).containsExactly("main", "feat").inOrder()
        }

    @Test
    fun `two listed album artists with one name stay apart`() =
        runTest {
            sync(
                albumArtists = listOf(artist("a", "Nirvana", albumArtist = true), artist("b", "Nirvana", true)),
                credited = listOf(artist("c", "Nirvana", albumArtist = false)),
                albumCredits = listOf("a", "c"),
            )

            val credits = libraryDao.artistAlbums("s", "a", true, emptyList()).first().single().credits
            // Two namesakes: no way to tell which one "c" is, so it stays as the server sent it (and is linked, so listed).
            assertThat(credits.map { it.link.artistId }).containsExactly("a", "c").inOrder()
            assertThat(libraryDao.albumArtists(true, emptyList()).first().map { it.id }).containsExactly("a", "b", "c")
        }

    @Test
    fun `a second sync gives the same library`() =
        runTest {
            satoshi()
            satoshi()

            assertThat(libraryDao.albumArtists(true, emptyList()).first().map { it.id }).containsExactly("listed")
            assertThat(libraryDao.artistAlbums("s", "listed", true, emptyList()).first()).hasSize(1)
        }
}
