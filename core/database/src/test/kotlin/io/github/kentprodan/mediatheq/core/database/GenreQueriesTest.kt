package io.github.kentprodan.mediatheq.core.database

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumGenreEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumGenreStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.ImageColumns
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackGenreEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackGenreStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.database.entity.UserDataColumns
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/**
 * Genres (#130). al1 (Rock, Folk) by ar1 holds t1 (Rock) and t2 (Folk); al2 (Rock) by ar2 holds t3 (no genre);
 * t4 has no album and is tagged Electrónica.
 */
class GenreQueriesTest {
    private val database = inMemoryDatabase()
    private val libraryDao = database.libraryDao()
    private val syncDao = database.syncDao()

    @After
    fun close() = database.close()

    private suspend fun sync(rockName: String = "Rock") {
        syncDao.stageAlbumArtists(listOf("ar1", "ar2").map { ArtistStaging(ArtistEntity("s", it, it, it, true)) })
        syncDao.stageAlbums(
            listOf(
                AlbumEntity("s", "al1", "Zebra", "Zebra", image = ImageColumns("al1", "tag1", "hash1")),
                AlbumEntity("s", "al2", "Apple", "Apple", image = ImageColumns("al2", "tag2", "hash2")),
            ).map(::AlbumStaging),
        )
        syncDao.stageAlbumArtistLinks(
            listOf(AlbumArtistEntity("s", "al1", 0, "ar1"), AlbumArtistEntity("s", "al2", 0, "ar2"))
                .map(::AlbumArtistStaging),
        )
        syncDao.stageTracks(
            listOf(
                TrackEntity("s", "t1", "One", "One", albumId = "al1"),
                TrackEntity("s", "t2", "Two", "Two", albumId = "al1"),
                TrackEntity("s", "t3", "Three", "Three", albumId = "al2"),
                TrackEntity("s", "t4", "Four", "Four"),
            ).map(::TrackStaging),
        )
        syncDao.stageAlbumGenreLinks(
            listOf(
                AlbumGenreEntity("s", "al1", "g-rock", rockName),
                AlbumGenreEntity("s", "al1", "g-folk", "Folk"),
                AlbumGenreEntity("s", "al2", "g-rock", rockName),
            ).map(::AlbumGenreStaging),
        )
        syncDao.stageTrackGenreLinks(
            listOf(
                TrackGenreEntity("s", "t1", "g-rock", rockName),
                TrackGenreEntity("s", "t2", "g-folk", "Folk"),
                TrackGenreEntity("s", "t4", "g-electro", "Electrónica"),
            ).map(::TrackGenreStaging),
        )
        syncDao.swap(1, "s", "user-1")
    }

    @Test
    fun `genres come from their items with counts and the first album's cover`() =
        runTest {
            sync()

            val genres = libraryDao.genres(true, emptyList()).first()

            assertThat(genres.map { it.name }).containsExactly("Electrónica", "Folk", "Rock").inOrder()
            val rock = genres.single { it.id == "g-rock" }
            assertThat(rock.albumCount).isEqualTo(2)
            assertThat(rock.trackCount).isEqualTo(1)
            // Apple comes before Zebra by name: its cover is the genre's.
            assertThat(rock.image).isEqualTo(ImageColumns("al2", "tag2", "hash2"))
            val electro = genres.single { it.id == "g-electro" }
            assertThat(electro.albumCount).isEqualTo(0)
            assertThat(electro.image.tag).isNull()
        }

    @Test
    fun `a genre's albums, album artists and tracks follow their own tags`() =
        runTest {
            sync()

            val albums = libraryDao.genreAlbums("s", "g-rock", true, emptyList()).first()
            val artists = libraryDao.genreArtists("s", "g-rock", true, emptyList()).first()
            val tracks = libraryDao.genreTracks("s", "g-rock", true, emptyList()).first()

            assertThat(albums.map { it.album.id }).containsExactly("al2", "al1").inOrder()
            assertThat(artists.map { it.id }).containsExactly("ar1", "ar2").inOrder()
            // t3 is on a Rock album but has no genre of its own: not a Rock track.
            assertThat(tracks.map { it.track.id }).containsExactly("t1")
        }

    @Test
    fun `offline a genre counts only downloads and one with none is left out`() =
        runTest {
            sync()
            val downloaded = listOf("t2")

            val genres = libraryDao.genres(false, downloaded).first()

            assertThat(genres.map { it.name }).containsExactly("Folk", "Rock").inOrder()
            val rock = genres.single { it.id == "g-rock" }
            // al1 holds the download, al2 does not; t1 (Rock) is not downloaded.
            assertThat(rock.albumCount).isEqualTo(1)
            assertThat(rock.trackCount).isEqualTo(0)
            assertThat(rock.image.itemId).isEqualTo("al1")
            assertThat(libraryDao.genreAlbums("s", "g-rock", false, downloaded).first().map { it.album.id })
                .containsExactly("al1")
            assertThat(libraryDao.genreArtists("s", "g-rock", false, downloaded).first().map { it.id })
                .containsExactly("ar1")
            assertThat(libraryDao.genreTracks("s", "g-folk", false, downloaded).first().map { it.track.id })
                .containsExactly("t2")
        }

    @Test
    fun `a genre renamed on the server shows its new name after the next sync`() =
        runTest {
            sync()
            sync(rockName = "Rock & Roll")

            assertThat(libraryDao.genres(true, emptyList()).first().map { it.name })
                .containsExactly("Electrónica", "Folk", "Rock & Roll")
                .inOrder()
        }

    @Test
    fun `search finds genres without diacritics`() =
        runTest {
            sync()

            val rows = libraryDao.search("electronica*", 10, true, emptyList())

            assertThat(rows.genreIds).containsExactly("g-electro")
        }

    @Test
    fun `sign-out forgets the genres`() =
        runTest {
            sync()

            syncDao.clearAll()

            assertThat(libraryDao.genres(true, emptyList()).first()).isEmpty()
        }

    @Test
    fun `every artist credited on the genre's tracks comes once, credit-only ones too, offline only on downloads`() =
        runTest {
            sync()
            syncDao.stageAlbumArtists(listOf("ar1", "ar2").map { ArtistStaging(ArtistEntity("s", it, it, it, true)) })
            syncDao.stageCreditedArtists(listOf(ArtistStaging(ArtistEntity("s", "guest", "Guest", "Guest", false))))
            syncDao.stageTracks(
                listOf(
                    TrackEntity("s", "t1", "One", "One", albumId = "al1"),
                    TrackEntity("s", "t5", "Five", "Five", albumId = "al2"),
                ).map(::TrackStaging),
            )
            syncDao.stageTrackArtistLinks(
                listOf(
                    TrackArtistEntity("s", "t1", 0, "ar1"),
                    TrackArtistEntity("s", "t1", 1, "guest"),
                    TrackArtistEntity("s", "t5", 0, "ar1"),
                ).map(::TrackArtistStaging),
            )
            syncDao.stageTrackGenreLinks(
                listOf(TrackGenreEntity("s", "t1", "g-rock", "Rock"), TrackGenreEntity("s", "t5", "g-rock", "Rock"))
                    .map(::TrackGenreStaging),
            )
            syncDao.swap(2, "s", "user-1")

            assertThat(libraryDao.genreTrackArtists("s", "g-rock", true, emptyList()).first().map { it.id })
                // By sort name: "Guest" sorts before "ar1".
                .containsExactly("guest", "ar1")
                .inOrder()
            assertThat(libraryDao.genreTrackArtists("s", "g-rock", false, listOf("t5")).first().map { it.id })
                .containsExactly("ar1")
            assertThat(libraryDao.genreTrackArtists("s", "g-folk", true, emptyList()).first()).isEmpty()
        }

    @Test
    fun `a genre's tracks keep their plays and length, offline only the downloaded ones, and counts follow`() =
        runTest {
            sync()
            syncDao.stageAlbumArtists(listOf("ar1", "ar2").map { ArtistStaging(ArtistEntity("s", it, it, it, true)) })
            syncDao.stageAlbums(listOf(AlbumEntity("s", "al1", "Zebra", "Zebra")).map(::AlbumStaging))
            syncDao.stageAlbumGenreLinks(listOf(AlbumGenreStaging(AlbumGenreEntity("s", "al1", "g-rock", "Rock"))))
            syncDao.stageTracks(
                listOf(
                    TrackEntity(
                        "s",
                        "t1",
                        "One",
                        "One",
                        albumId = "al1",
                        durationMillis = 60_000,
                        userData = UserDataColumns(playCount = 7),
                    ),
                    TrackEntity("s", "t2", "Two", "Two", albumId = "al1"),
                ).map(::TrackStaging),
            )
            syncDao.stageTrackGenreLinks(
                listOf(TrackGenreEntity("s", "t1", "g-rock", "Rock"), TrackGenreEntity("s", "t2", "g-rock", "Rock"))
                    .map(::TrackGenreStaging),
            )
            syncDao.swap(2, "s", "user-1")

            val online = libraryDao.genreTracks("s", "g-rock", true, emptyList()).first()
            assertThat(online.map { it.track.id }).containsExactly("t1", "t2").inOrder()
            assertThat(
                online
                    .first()
                    .track.userData.playCount,
            ).isEqualTo(7)
            assertThat(online.first().track.durationMillis).isEqualTo(60_000)
            val offline = libraryDao.genreTracks("s", "g-rock", false, listOf("t1")).first()
            assertThat(offline.map { it.track.id }).containsExactly("t1")
            val rock = libraryDao.genres(false, listOf("t1")).first().single { it.id == "g-rock" }
            assertThat(rock.trackCount).isEqualTo(1)
            assertThat(rock.albumCount).isEqualTo(1)
        }
}
