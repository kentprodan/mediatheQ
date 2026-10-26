package io.github.kentprodan.mediatheq.core.data

import androidx.room3.useWriterConnection
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.errorOrNull
import io.github.kentprodan.mediatheq.core.common.getOrNull
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.database.entity.UserDataColumns
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class DefaultLibraryRepositoryTest {
    private val database = inMemoryDatabase()
    private val preferences = FakeLibraryPreferencesRepository()
    private val network = FakeNetworkMonitor()
    private val downloads = FakeDownloadRepository()
    private val repository =
        DefaultLibraryRepository(database.libraryDao(), OfflineFilter(preferences, network) { downloads })

    @After
    fun close() = database.close()

    private suspend fun library(vararg titles: String) {
        database.syncDao().stageTracks(
            titles.mapIndexed {
                i,
                title,
                ->
                TrackStaging(TrackEntity("s", "t$i", title, title))
            },
        )
        database.syncDao().swap(1, "s", "user-1")
    }

    private suspend fun searchTitles(query: String) =
        repository
            .search(query)
            .getOrNull()!!
            .tracks
            .map { it.title }

    private fun downloaded(vararg ids: String) {
        downloads.downloads.value =
            ids.map {
                TrackDownload(
                    Track(MediaId(ServerId("s"), it), it, emptyList(), 1.minutes),
                    DownloadState.Completed,
                )
            }
    }

    @Test
    fun `offline mode lists only downloads, and turning it off brings the whole library back`() =
        runTest {
            library("Pe-o margine", "Satele", "Drum")
            downloaded("t1")

            repository.tracks().test {
                assertThat(awaitItem().map { it.title }).containsExactly("Drum", "Pe-o margine", "Satele").inOrder()
                preferences.setOfflineMode(true)
                assertThat(awaitItem().map { it.title }).containsExactly("Satele")
                preferences.setOfflineMode(false)
                assertThat(awaitItem().map { it.title }).hasSize(3)
            }
        }

    @Test
    fun `add to playlist still sees every playlist in offline mode`() =
        runTest {
            database.playlistDao().replace(PlaylistEntity("s", "p1", "Drum", "Drum"), emptyList())
            preferences.setOfflineMode(true)

            assertThat(repository.playlists().first()).isEmpty()
            assertThat(repository.allPlaylists().first().map { it.name }).containsExactly("Drum")
        }

    @Test
    fun `the Downloads tab still names every track in offline mode`() =
        runTest {
            library("Pe-o margine", "Satele")
            preferences.setOfflineMode(true)

            assertThat(repository.tracks().first()).isEmpty()
            assertThat(repository.allTracks().first()).hasSize(2)
        }

    @Test
    fun `without a network offline mode is on by itself`() =
        runTest {
            library("Pe-o margine", "Satele")
            downloaded("t0")

            network.online.value = false

            assertThat(repository.offline.first()).isTrue()
            assertThat(repository.tracks().first().map { it.title }).containsExactly("Pe-o margine")
            assertThat(searchTitles("sat")).isEmpty()
        }

    @Test
    fun `a download that completes shows at once in offline mode`() =
        runTest {
            library("Pe-o margine", "Satele")
            preferences.setOfflineMode(true)

            repository.tracks().test {
                assertThat(awaitItem()).isEmpty()
                downloaded("t1")
                assertThat(awaitItem().map { it.title }).containsExactly("Satele")
            }
        }

    @Test
    fun `track reads one track with its user data, null when missing`() =
        runTest {
            library("Pe-o margine")
            database.userDataDao().setUserData("s", "t0", UserDataColumns(isFavorite = true, rating = 8.0))

            val track = repository.track(MediaId(ServerId("s"), "t0")).first()
            assertThat(track?.title).isEqualTo("Pe-o margine")
            assertThat(track?.userData?.isFavorite).isTrue()
            assertThat(track?.userData?.halfStars).isEqualTo(8)
            assertThat(repository.track(MediaId(ServerId("s"), "gone")).first()).isNull()
        }

    @Test
    fun `the audio format and its spatial format survive the database`() =
        runTest {
            val id = MediaId(ServerId("s"), "t0")
            val format =
                AudioFormat("eac3", sampleRateHz = 48_000, channels = 6, spatialFormat = SpatialFormat.DolbyAtmos)
            database.syncDao().stageTracks(
                listOf(TrackStaging(Track(id, "Atmos", emptyList(), 4.minutes, audioFormat = format).toEntity())),
            )
            database.syncDao().swap(1, "s", "user-1")

            assertThat(repository.track(id).first()?.audioFormat).isEqualTo(format)
        }

    @Test
    fun `a database failure becomes a storage error`() =
        runTest {
            // A broken index file shows up as an SQLite error on the query.
            database.useWriterConnection { connection -> connection.usePrepared("DROP TABLE search") { it.step() } }

            assertThat(repository.search("mos").errorOrNull()).isEqualTo(MqError.Storage)
        }

    @Test
    fun `typed words become quoted terms and the last one a prefix`() {
        assertThat(ftsMatchExpression("  ada  mos ")).isEqualTo("\"ada\" \"mos\"*")
        assertThat(ftsMatchExpression("say \"hi\"")).isEqualTo("\"say\" \"\"\"hi\"\"\"*")
        assertThat(ftsMatchExpression("   ")).isNull()
    }

    @Test
    fun `search finds by prefix, without diacritics`() =
        runTest {
            library("Moșoi", "Țară")

            assertThat(searchTitles("mos")).containsExactly("Moșoi")
            assertThat(searchTitles("tara")).containsExactly("Țară")
            assertThat(
                repository
                    .search("mos")
                    .getOrNull()!!
                    .tracks
                    .single()
                    .id,
            ).isEqualTo(MediaId(ServerId("s"), "t0"))
        }

    @Test
    fun `text that is FTS syntax is searched as plain text, never an error`() =
        runTest {
            library("AC/DC", "Rock-n-roll", "Do NOT disturb", "Re: Union", "Say \"hi\"")

            listOf("AC/DC", "rock-n-roll", "NOT", "re:", "\"", "say \"hi", "*", "(", "OR", "-").forEach { query ->
                assertThat(repository.search(query).getOrNull()).isNotNull()
            }
            assertThat(searchTitles("AC/DC")).containsExactly("AC/DC")
            assertThat(searchTitles("NOT")).containsExactly("Do NOT disturb")
        }

    @Test
    fun `an empty query finds nothing`() =
        runTest {
            library("Moșoi")

            assertThat(repository.search("  ").getOrNull()?.isEmpty()).isTrue()
        }

    @Test
    fun `an album and its tracks map to the domain`() =
        runTest {
            database.syncDao().stageAlbums(
                listOf(
                    io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging(
                        io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity(
                            "s",
                            "al1",
                            "Satelit",
                            "Satelit",
                            year = 2016,
                            normalizationGainDb = -7.5,
                        ),
                    ),
                ),
            )
            database.syncDao().stageTracks(
                listOf(
                    TrackStaging(
                        TrackEntity(
                            "s",
                            "t1",
                            "Moșoi",
                            "Mosoi",
                            albumId = "al1",
                            trackNumber = 1,
                            durationMillis = 1_000,
                            normalizationGainDb = -6.0,
                        ),
                    ),
                ),
            )
            database.syncDao().swap(1, "s", "user-1")
            val id = MediaId(ServerId("s"), "al1")

            assertThat(repository.album(id).first()?.year).isEqualTo(2016)
            val track = repository.albumTracks(id).first().single()
            assertThat(track.albumId).isEqualTo(id)
            assertThat(track.duration.inWholeMilliseconds).isEqualTo(1_000)
            assertThat(track.normalizationGainDb).isEqualTo(-6.0)
            assertThat(track.albumNormalizationGainDb).isEqualTo(-7.5)
        }

    @Test
    fun `an artist, its albums and tracks map to the domain`() =
        runTest {
            val syncDao = database.syncDao()
            syncDao.stageAlbumArtists(listOf(ArtistStaging(ArtistEntity("s", "ar1", "Subcarpați", "Subcarpati", true))))
            syncDao.stageAlbums(listOf(AlbumStaging(AlbumEntity("s", "al1", "Satelit", "Satelit", year = 2016))))
            syncDao.stageAlbumArtistLinks(listOf(AlbumArtistStaging(AlbumArtistEntity("s", "al1", 0, "ar1"))))
            syncDao.stageTracks(listOf(TrackStaging(TrackEntity("s", "t1", "Moșoi", "Mosoi", albumId = "al1"))))
            syncDao.swap(1, "s", "user-1")
            val id = MediaId(ServerId("s"), "ar1")

            assertThat(repository.artist(id).first()?.name).isEqualTo("Subcarpați")
            assertThat(
                repository
                    .artistAlbums(id)
                    .first()
                    .single()
                    .albumArtists
                    .single()
                    .id,
            ).isEqualTo(id)
            assertThat(repository.appearsOn(id).first()).isEmpty()
            assertThat(repository.artistTracks(id).first().map { it.title }).containsExactly("Moșoi")
        }
}
