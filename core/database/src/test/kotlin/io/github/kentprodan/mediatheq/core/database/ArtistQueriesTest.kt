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
import org.junit.Before
import org.junit.Test

class ArtistQueriesTest {
    private val database = inMemoryDatabase()
    private val libraryDao = database.libraryDao()

    @After
    fun close() = database.close()

    // ar1 has three albums (one without a year) and appears on ar2's compilation; ar3 is credit-only, featured on al1.
    @Before
    fun library() =
        runTest {
            val syncDao = database.syncDao()
            syncDao.stageAlbumArtists(listOf(artist("ar1"), artist("ar2")).map(::ArtistStaging))
            syncDao.stageCreditedArtists(listOf(ArtistStaging(artist("ar3").copy(isAlbumArtist = false))))
            syncDao.stageAlbums(
                listOf(album("al1", 2016), album("al2", 2020), album("al3", null), album("al4", 2018))
                    .map(::AlbumStaging),
            )
            syncDao.stageAlbumArtistLinks(
                listOf("al1" to "ar1", "al2" to "ar1", "al3" to "ar1", "al4" to "ar2").map { (album, artist) ->
                    AlbumArtistStaging(AlbumArtistEntity("s", album, 0, artist))
                },
            )
            syncDao.stageTracks(
                listOf(
                    track("t1", "al1", 1),
                    track("t2", "al1", 2),
                    track("t3", "al2", 1),
                    track("t4", "al4", 1),
                    track("t5", "al4", 2),
                    track("t6", null, null),
                    track("t7", "al3", 1),
                ).map(::TrackStaging),
            )
            syncDao.stageTrackArtistLinks(
                listOf(
                    TrackArtistEntity("s", "t1", 0, "ar1"),
                    TrackArtistEntity("s", "t2", 0, "ar3"),
                    // Credited twice: must still come back once.
                    TrackArtistEntity("s", "t3", 0, "ar1"),
                    TrackArtistEntity("s", "t3", 1, "ar1"),
                    TrackArtistEntity("s", "t4", 0, "ar1"),
                    TrackArtistEntity("s", "t5", 0, "ar2"),
                    TrackArtistEntity("s", "t6", 0, "ar1"),
                    TrackArtistEntity("s", "t7", 0, "ar1"),
                ).map(::TrackArtistStaging),
            )
            syncDao.swap(1, "s", "user-1")
        }

    private fun artist(id: String) = ArtistEntity("s", id, id, id, isAlbumArtist = true)

    private fun album(
        id: String,
        year: Int?,
    ) = AlbumEntity("s", id, id, id, year = year)

    private fun track(
        id: String,
        albumId: String?,
        number: Int?,
    ) = TrackEntity("s", id, id, id, albumId = albumId, trackNumber = number)

    @Test
    fun `an artist is found whether album artist or credit-only`() =
        runTest {
            assertThat(libraryDao.artist("s", "ar1").first()?.id).isEqualTo("ar1")
            assertThat(libraryDao.artist("s", "ar3").first()?.id).isEqualTo("ar3")
            assertThat(libraryDao.artist("s", "nope").first()).isNull()
        }

    @Test
    fun `own albums come newest first with unknown year last`() =
        runTest {
            assertThat(libraryDao.artistAlbums("s", "ar1").first().map { it.album.id })
                .containsExactly("al2", "al1", "al3")
                .inOrder()
        }

    @Test
    fun `appears on lists albums with a credited track but not the artist's own`() =
        runTest {
            assertThat(libraryDao.appearsOnAlbums("s", "ar1").first().map { it.album.id }).containsExactly("al4")
            assertThat(libraryDao.appearsOnAlbums("s", "ar3").first().map { it.album.id }).containsExactly("al1")
            assertThat(libraryDao.artistAlbums("s", "ar3").first()).isEmpty()
        }

    @Test
    fun `artist tracks hold every track once, own albums first and albumless tracks last`() =
        runTest {
            // t2 credits only ar3 but is on ar1's album; t5 is on the compilation without ar1.
            assertThat(libraryDao.artistTracks("s", "ar1").first().map { it.track.id })
                .containsExactly("t3", "t1", "t2", "t7", "t4", "t6")
                .inOrder()
        }

    @Test
    fun `a credit-only artist's tracks are the credited ones`() =
        runTest {
            assertThat(libraryDao.artistTracks("s", "ar3").first().map { it.track.id }).containsExactly("t2")
        }
}
