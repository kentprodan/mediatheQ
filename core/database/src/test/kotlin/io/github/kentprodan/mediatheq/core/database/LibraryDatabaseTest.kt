package io.github.kentprodan.mediatheq.core.database

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.ImageColumns
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistStaging
import io.github.kentprodan.mediatheq.core.database.entity.SyncStateEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.database.entity.UserDataColumns
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import io.github.kentprodan.mediatheq.core.testing.rows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class LibraryDatabaseTest {
    private val database = inMemoryDatabase()
    private val syncDao = database.syncDao()
    private val libraryDao = database.libraryDao()

    @After
    fun close() = database.close()

    private val artist = ArtistEntity("s", "ar1", "Subcarpați", "Subcarpati", isAlbumArtist = true)
    private val album =
        AlbumEntity(
            "s",
            "al1",
            "Satelit",
            "Satelit",
            year = 2016,
            trackCount = 1,
            durationMillis = 200_000,
            dateAddedMillis = 1L,
            normalizationGainDb = -7.5,
            image = ImageColumns("al1", "tag", "hash"),
            userData =
                UserDataColumns(
                    isFavorite = true,
                    rating = 8.0,
                    playCount = 2,
                    lastPlayedAtMillis = 5L,
                    played = true,
                ),
        )
    private val track =
        TrackEntity(
            "s",
            "t1",
            "Moșoi",
            "Mosoi",
            albumId = "al1",
            albumTitle = "Satelit",
            trackNumber = 1,
            discNumber = 1,
            durationMillis = 200_000,
            codec = "flac",
            sampleRateHz = 44_100,
            bitDepth = 16,
            channels = 2,
            bitrateKbps = 918,
            spatialFormat = "DolbyAtmos",
        )
    private val playlist = PlaylistEntity("s", "p1", "Drum", "Drum", trackCount = 2)

    private suspend fun stageLibrary(
        artists: List<ArtistEntity> = listOf(artist),
        albums: List<AlbumEntity> = listOf(album),
        tracks: List<TrackEntity> = listOf(track),
    ) {
        syncDao.stageAlbumArtists(artists.map(::ArtistStaging))
        syncDao.stageAlbums(albums.map(::AlbumStaging))
        syncDao.stageAlbumArtistLinks(albums.map { AlbumArtistStaging(AlbumArtistEntity("s", it.id, 0, "ar1")) })
        syncDao.stageTracks(tracks.map(::TrackStaging))
        syncDao.stageTrackArtistLinks(tracks.map { TrackArtistStaging(TrackArtistEntity("s", it.id, 0, "ar1")) })
        syncDao.stagePlaylists(listOf(PlaylistStaging(playlist)))
        syncDao.stagePlaylistEntries(
            listOf(
                PlaylistEntryStaging(PlaylistEntryEntity("s", "p1", 0, "e1", "t1")),
                // An entry whose track is not in the library: kept, and must not break the swap.
                PlaylistEntryStaging(PlaylistEntryEntity("s", "p1", 1, "e2", "missing")),
            ),
        )
    }

    @Test
    fun `staged rows stay invisible until the swap`() =
        runTest {
            libraryDao.albums().test {
                assertThat(awaitItem()).isEmpty()

                stageLibrary()
                expectNoEvents()

                syncDao.swap(42, "s", "user-1")
                val albums = awaitItem()
                assertThat(albums.single().album).isEqualTo(album)
                assertThat(
                    albums
                        .single()
                        .credits
                        .single()
                        .artist,
                ).isEqualTo(artist)
            }
        }

    @Test
    fun `the swap keeps every column`() =
        runTest {
            stageLibrary()

            syncDao.swap(42, "s", "user-1")

            assertThat(
                libraryDao
                    .albums()
                    .first()
                    .single()
                    .album,
            ).isEqualTo(album)
            assertThat(database.rows("SELECT title, spatial_format, bitrate_kbps FROM track"))
                .containsExactly(listOf("Moșoi", "DolbyAtmos", "918"))
            assertThat(database.rows("SELECT entry_id, track_id FROM playlist_entry ORDER BY position"))
                .containsExactly(listOf("e1", "t1"), listOf("e2", "missing"))
                .inOrder()
        }

    @Test
    fun `the swap removes what the new sync no longer has and empties staging`() =
        runTest {
            stageLibrary(albums = listOf(album, album.copy(id = "al2", title = "Gone", sortName = "Gone")))
            syncDao.swap(1, "s", "user-1")

            stageLibrary()
            syncDao.swap(2, "s", "user-1")

            assertThat(libraryDao.albums().first().map { it.album.id }).containsExactly("al1")
            assertThat(database.rows("SELECT COUNT(*) FROM album_staging")).containsExactly(listOf("0"))
            assertThat(database.rows("SELECT COUNT(*) FROM search WHERE item_id = 'al2'")).containsExactly(listOf("0"))
        }

    @Test
    fun `the swap records the finished sync and clears the checkpoint`() =
        runTest {
            syncDao.saveState(
                SyncStateEntity(checkpointServerId = "s", checkpointStep = "TRACKS", checkpointIndex = 500),
            )
            stageLibrary()

            syncDao.swap(42, "s", "user-1")

            assertThat(syncDao.state()).isEqualTo(SyncStateEntity(0, 42, "s", "user-1"))
        }

    @Test
    fun `running the swap twice is harmless`() =
        runTest {
            stageLibrary()
            syncDao.swap(1, "s", "user-1")

            syncDao.swap(2, "s", "user-1")

            // Staging was empty the second time, so the library is replaced by nothing - callers only swap after a
            // complete sync, and a crash mid-swap rolls the whole transaction back.
            assertThat(libraryDao.albums().first()).isEmpty()
            assertThat(syncDao.state()?.lastFullSyncAtMillis).isEqualTo(2)
        }

    @Test
    fun `discarding staging keeps the live library and the last sync time`() =
        runTest {
            stageLibrary()
            syncDao.swap(42, "s", "user-1")
            stageLibrary(albums = listOf(album.copy(title = "Half")))
            syncDao.saveState(SyncStateEntity(lastFullSyncAtMillis = 42, checkpointStep = "ALBUMS"))

            syncDao.discardStaging()

            assertThat(
                libraryDao
                    .albums()
                    .first()
                    .single()
                    .album.title,
            ).isEqualTo("Satelit")
            assertThat(syncDao.state()).isEqualTo(SyncStateEntity(lastFullSyncAtMillis = 42))
        }

    @Test
    fun `a staged page and its checkpoint are written together`() =
        runTest {
            val checkpoint = SyncStateEntity(checkpointStep = "ALBUMS", checkpointIndex = 500)

            syncDao.stagePage(checkpoint) { stageAlbums(listOf(AlbumStaging(album))) }

            assertThat(syncDao.state()).isEqualTo(checkpoint)
            assertThat(database.rows("SELECT id FROM album_staging")).containsExactly(listOf("al1"))
        }

    @Test
    fun `credited artists never overwrite album artists`() =
        runTest {
            syncDao.stageAlbumArtists(listOf(ArtistStaging(artist)))

            syncDao.stageCreditedArtists(listOf(ArtistStaging(artist.copy(isAlbumArtist = false, name = "x"))))

            assertThat(
                database.rows("SELECT name, is_album_artist FROM artist_staging"),
            ).containsExactly(listOf("Subcarpați", "1"))
        }

    @Test
    fun `staged playlist ids come in sort order`() =
        runTest {
            syncDao.stagePlaylists(
                listOf(
                    PlaylistStaging(playlist.copy(id = "p2", sortName = "B")),
                    PlaylistStaging(playlist.copy(sortName = "A")),
                ),
            )

            assertThat(syncDao.stagedPlaylistIds()).containsExactly("p1", "p2").inOrder()
        }

    @Test
    fun `search ignores diacritics in both directions`() =
        runTest {
            stageLibrary(
                tracks =
                    listOf(
                        track,
                        track.copy(id = "t2", title = "Moşoi (cedilla)"),
                        track.copy(id = "t3", title = "Țară"),
                        track.copy(id = "t4", title = "Beyoncé"),
                    ),
            )
            syncDao.swap(1, "s", "user-1")

            assertThat(libraryDao.search("mosoi", 10).map { it.itemId }).containsExactly("t1", "t2")
            assertThat(libraryDao.search("tara", 10).map { it.itemId }).containsExactly("t3")
            assertThat(libraryDao.search("beyonce", 10).map { it.itemId }).containsExactly("t4")
            assertThat(libraryDao.search("Moșoi", 10).map { it.itemId }).containsExactly("t1", "t2")
        }

    @Test
    fun `search covers album artists, albums and playlists but not credit-only artists`() =
        runTest {
            stageLibrary(
                artists = listOf(artist, artist.copy(id = "ar2", name = "Subcarpați Feat", isAlbumArtist = false)),
            )
            syncDao.swap(1, "s", "user-1")

            assertThat(
                libraryDao.search("subcarpati", 10).map { it.kind to it.itemId },
            ).containsExactly("artist" to "ar1")
            assertThat(libraryDao.search("satelit", 10).map { it.kind }).containsExactly("album")
            assertThat(libraryDao.search("drum", 10).map { it.kind }).containsExactly("playlist")
            assertThat(libraryDao.search("mos*", 10).map { it.kind }).containsExactly("track")
        }

    @Test
    fun `clearAll empties the library, staging, search and sync state`() =
        runTest {
            stageLibrary()
            syncDao.swap(1, "s", "user-1")
            stageLibrary()

            syncDao.clearAll()

            listOf("artist", "album", "track", "playlist_entry", "track_staging", "search", "sync_state").forEach {
                assertThat(database.rows("SELECT COUNT(*) FROM $it")).containsExactly(listOf("0"))
            }
        }

    @Test
    fun `the artists tab lists album artists only`() =
        runTest {
            stageLibrary(
                artists =
                    listOf(
                        artist,
                        artist.copy(id = "ar2", name = "Guest", sortName = "Guest", isAlbumArtist = false),
                    ),
            )
            syncDao.swap(1, "s", "user-1")

            assertThat(libraryDao.albumArtists().first().map { it.id }).containsExactly("ar1")
        }

    @Test
    fun `playlists come by sort name`() =
        runTest {
            syncDao.stagePlaylists(
                listOf(
                    PlaylistStaging(playlist.copy(id = "p2", sortName = "B")),
                    PlaylistStaging(playlist.copy(sortName = "A")),
                ),
            )
            syncDao.swap(1, "s", "user-1")

            assertThat(libraryDao.playlists().first().map { it.id }).containsExactly("p1", "p2").inOrder()
        }

    @Test
    fun `tracks come with their credits, also when an artist is missing`() =
        runTest {
            stageLibrary()
            syncDao.stageTrackArtistLinks(listOf(TrackArtistStaging(TrackArtistEntity("s", "t1", 1, "not-synced"))))
            syncDao.swap(1, "s", "user-1")

            val row = libraryDao.tracks().first().single()
            assertThat(row.track).isEqualTo(track)
            assertThat(
                row.credits.sortedBy { it.link.position }.map { it.artist?.id },
            ).containsExactly("ar1", null).inOrder()
        }

    @Test
    fun `one album by its full id, null when unknown or on another server`() =
        runTest {
            stageLibrary()
            syncDao.swap(1, "s", "user-1")

            assertThat(libraryDao.album("s", "al1").first()?.album).isEqualTo(album)
            assertThat(libraryDao.album("s", "nope").first()).isNull()
            assertThat(libraryDao.album("other", "al1").first()).isNull()
        }

    @Test
    fun `album tracks come in play order with credits, only from that server and album`() =
        runTest {
            stageLibrary(
                tracks =
                    listOf(
                        track.copy(id = "d2t1", discNumber = 2, trackNumber = 1, title = "B"),
                        track.copy(id = "d1t2", discNumber = 1, trackNumber = 2),
                        track.copy(id = "d1-none", discNumber = 1, trackNumber = null, sortName = "Z"),
                        track.copy(id = "nodisc-t1", discNumber = null, trackNumber = 1),
                        track.copy(id = "other-album", albumId = "al2"),
                    ),
            )
            syncDao.stageTracks(listOf(TrackStaging(track.copy(serverId = "other", id = "foreign"))))
            syncDao.swap(1, "s", "user-1")

            val rows = libraryDao.albumTracks("s", "al1").first()

            assertThat(rows.map { it.track.id }).containsExactly("nodisc-t1", "d1t2", "d1-none", "d2t1").inOrder()
            assertThat(rows.first().credits.map { it.artist?.id }).containsExactly("ar1")
        }
}
