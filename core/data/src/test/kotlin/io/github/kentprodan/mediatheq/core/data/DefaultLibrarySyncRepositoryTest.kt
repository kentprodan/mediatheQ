package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.database.entity.SyncStateEntity
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySource
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataSink
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import io.github.kentprodan.mediatheq.core.testing.TestClock
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import io.github.kentprodan.mediatheq.core.testing.rows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class DefaultLibrarySyncRepositoryTest {
    private val server = ServerId("s")
    private val dispatcher = StandardTestDispatcher()
    private val database = inMemoryDatabase(dispatcher)
    private val accountStore = AccountStore(InMemoryPreferencesDataStore(), FakeTokenCipher())
    private val session = FakeSessionRepository()
    private val clock = TestClock()
    private val source = FakeLibrarySource()
    private val library = DefaultLibraryRepository(database.libraryDao())

    private fun id(value: String) = MediaId(server, value)

    private val artist = Artist(id("ar1"), "Subcarpați")

    @After
    fun close() = database.close()

    private fun fillSource(
        trackCount: Int = 1_200,
        albumTitle: String = "Satelit",
    ) {
        source.albumArtists = listOf(artist)
        source.albums =
            listOf(Album(id("al1"), albumTitle, listOf(artist)), Album(id("al2"), "Underground", listOf(artist)))
        source.tracks =
            List(trackCount) {
                Track(
                    id("t$it"),
                    "Track $it",
                    listOf(artist, Artist(id("feat"), "Guest")),
                    Duration.ZERO,
                    albumId = id("al1"),
                )
            }
        source.playlists = listOf(Playlist(id("p1"), "Drum"), Playlist(id("p2"), "Empty"))
        source.entries = mapOf(id("p1") to List(3) { PlaylistEntry("e$it", id("t$it")) })
    }

    private suspend fun signIn() =
        accountStore.save(StoredAccount("http://nas:8096", server, "Home", "user-1", "Ana", accessToken = "token"))

    private val userDataSink = FakeUserDataSink()

    private fun userData(scope: CoroutineScope) =
        DefaultUserDataRepository(database.userDataDao(), userDataSink, accountStore, session, scope)

    private fun TestScope.repository(scope: CoroutineScope = backgroundScope) =
        DefaultLibrarySyncRepository(source, accountStore, database.syncDao(), session, clock, scope, userData(scope))

    private suspend fun count(table: String) =
        database
            .rows("SELECT COUNT(*) FROM $table")
            .single()
            .single()
            ?.toInt()

    @Test
    fun `a full sync fills the library, pages in order and records the time`() =
        runTest(dispatcher) {
            fillSource()
            signIn()
            val repository = repository()

            assertThat(repository.sync()).isEqualTo(MqResult.Success(Unit))

            assertThat(source.calls)
                .containsExactly(
                    "albumArtists@0",
                    "albums@0",
                    "tracks@0",
                    "tracks@500",
                    "tracks@1000",
                    "playlists@0",
                    "entries:p1@0",
                    "entries:p2@0",
                ).inOrder()
            assertThat(library.albums().first().map { it.title }).containsExactly("Satelit", "Underground").inOrder()
            assertThat(
                library
                    .albums()
                    .first()
                    .first()
                    .albumArtists,
            ).containsExactly(artist)
            assertThat(count("track")).isEqualTo(1_200)
            assertThat(count("playlist_entry")).isEqualTo(3)
            assertThat(database.rows("SELECT name, is_album_artist FROM artist ORDER BY id"))
                .containsExactly(listOf("Subcarpați", "1"), listOf("Guest", "0"))
            assertThat(repository.status.value).isEqualTo(SyncStatus.Idle(clock.now))
        }

    @Test
    fun `readers keep the old library until the new one is complete`() =
        runTest(dispatcher) {
            fillSource()
            signIn()
            val repository = repository()
            repository.sync()
            fillSource(albumTitle = "Satelit (Deluxe)")
            source.calls.clear()
            source.pauseAt = 4

            val second = async { repository.sync() }
            source.paused.await()
            assertThat(library.albums().first().map { it.title }).contains("Satelit")
            assertThat(repository.status.value).isInstanceOf(SyncStatus.Running::class.java)

            source.resume.complete(Unit)
            second.await()
            assertThat(library.albums().first().map { it.title }).contains("Satelit (Deluxe)")
        }

    @Test
    fun `a failure keeps the old library and the checkpoint`() =
        runTest(dispatcher) {
            fillSource()
            signIn()
            val repository = repository()
            repository.sync()
            fillSource(albumTitle = "Changed")
            source.calls.clear()
            source.failAt = 4 // tracks@500

            val result = repository.sync()

            assertThat(result).isEqualTo(MqResult.Failure(MqError.Network))
            assertThat(repository.status.value).isEqualTo(SyncStatus.Failed(MqError.Network, clock.now))
            assertThat(library.albums().first().map { it.title }).contains("Satelit")
            val state = database.syncDao().state()
            assertThat(state?.checkpointStep).isEqualTo("Tracks")
            assertThat(state?.checkpointIndex).isEqualTo(500)
        }

    @Test
    fun `a sync interrupted by process death resumes where it stopped`() =
        runTest(dispatcher) {
            fillSource()
            signIn()
            val firstLife = CoroutineScope(backgroundScope.coroutineContext + Job())
            source.pauseAt = 4
            firstLife.launch { repository(firstLife).sync() }
            source.paused.await()
            firstLife.cancel() // the process dies while tracks@500 is in flight

            val secondSource = FakeLibrarySource()
            val restarted =
                DefaultLibrarySyncRepository(
                    secondSource,
                    accountStore,
                    database.syncDao(),
                    session,
                    clock,
                    backgroundScope,
                    userData(backgroundScope),
                )
            secondSource.albumArtists = source.albumArtists
            secondSource.albums = source.albums
            secondSource.tracks = source.tracks
            secondSource.playlists = source.playlists
            secondSource.entries = source.entries

            assertThat(restarted.sync()).isEqualTo(MqResult.Success(Unit))
            assertThat(secondSource.calls.first()).isEqualTo("tracks@500")
            assertThat(count("track")).isEqualTo(1_200)
            assertThat(library.albums().first()).hasSize(2)
        }

    @Test
    fun `a checkpoint older than an hour is thrown away`() =
        runTest(dispatcher) {
            fillSource()
            signIn()
            val repository = repository()
            source.failAt = 4
            repository.sync()
            source.failAt = null
            source.calls.clear()
            clock.advanceBy(2.hours)

            repository.sync()

            assertThat(source.calls.first()).isEqualTo("albumArtists@0")
        }

    @Test
    fun `a 401 signs out once and is not retried`() =
        runTest(dispatcher) {
            fillSource()
            signIn()
            source.failAt = 1
            source.failure = MqError.Unauthorized

            val result = repository().sync()

            assertThat(result).isEqualTo(MqResult.Failure(MqError.Unauthorized))
            assertThat(source.calls).hasSize(1)
            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut(LogoutReason.SessionExpired))
        }

    @Test
    fun `two triggers at once run a single sync`() =
        runTest(dispatcher) {
            fillSource(trackCount = 10)
            signIn()
            val repository = repository()
            source.pauseAt = 1

            val first = async { repository.sync() }
            source.paused.await()
            val second = async { repository.sync() }
            yield()
            source.resume.complete(Unit)

            assertThat(first.await()).isEqualTo(MqResult.Success(Unit))
            assertThat(second.await()).isEqualTo(MqResult.Success(Unit))
            assertThat(source.calls.count { it == "albumArtists@0" }).isEqualTo(1)
        }

    @Test
    fun `signing in starts the first sync`() =
        runTest(dispatcher) {
            fillSource(trackCount = 10)
            val repository = repository()
            repository.start()

            signIn()

            repository.status.first { it is SyncStatus.Idle && it.lastSyncedAt != null }
            assertThat(count("track")).isEqualTo(10)
        }

    @Test
    fun `a sync is due when never done, resumable, or over a day old`() =
        runTest(dispatcher) {
            val repository = repository()
            val account = "s" to "user-1"
            val dao = database.syncDao()
            assertThat(repository.isDue(account)).isTrue()

            dao.saveState(SyncStateEntity(lastFullSyncAtMillis = (clock.now - 23.hours).toEpochMilliseconds()))
            assertThat(repository.isDue(account)).isFalse()

            dao.saveState(SyncStateEntity(lastFullSyncAtMillis = (clock.now - 25.hours).toEpochMilliseconds()))
            assertThat(repository.isDue(account)).isTrue()

            val recent = (clock.now - 1.hours).toEpochMilliseconds()
            dao.saveState(
                SyncStateEntity(
                    lastFullSyncAtMillis = recent,
                    checkpointServerId = "s",
                    checkpointUserId = "user-1",
                    checkpointStartedAtMillis = recent,
                    checkpointStep = "Tracks",
                    checkpointIndex = 500,
                ),
            )
            assertThat(repository.isDue(account)).isTrue()
            assertThat(repository.isDue("s" to "someone-else")).isFalse()
        }

    @Test
    fun `signing out wipes the library, also a sync in flight`() =
        runTest(dispatcher) {
            fillSource()
            signIn()
            val repository = repository()
            source.pauseAt = 3
            repository.start()
            source.paused.await()

            accountStore.clear()
            repository.status.first { it == SyncStatus.Idle(null) }
            source.resume.complete(Unit)
            advanceUntilIdle()

            listOf("artist", "album", "track", "playlist_entry", "track_staging", "search", "sync_state").forEach {
                assertThat(count(it)).isEqualTo(0)
            }
            assertThat(repository.status.value).isEqualTo(SyncStatus.Idle(null))
        }

    @Test
    fun `R51 an unexpected failure in a sync does not stop the account watch, so signing out still wipes`() =
        runTest(dispatcher) {
            fillSource()
            signIn()
            val repository = repository()
            source.throwAt = 4
            repository.start()
            val failed = repository.status.first { it is SyncStatus.Failed } as SyncStatus.Failed
            assertThat(failed.error).isInstanceOf(MqError.Unknown::class.java)
            assertThat(count("track_staging")).isGreaterThan(0)

            accountStore.clear()
            repository.status.first { it == SyncStatus.Idle(null) }
            advanceUntilIdle()

            ALL_TABLES.forEach { assertThat(count(it)).isEqualTo(0) }
        }

    @Test
    fun `R51 a caller waiting on a sync that signing out cancels gets a failure, not a cancellation`() =
        runTest(dispatcher) {
            fillSource()
            signIn()
            val repository = repository()
            source.pauseAt = 3
            repository.start()
            source.paused.await()
            // Like live sync's loop: a cancellation thrown at it would end it for the app's lifetime.
            val waiting = async { runCatching { repository.sync() } }
            runCurrent()

            accountStore.clear()
            repository.status.first { it == SyncStatus.Idle(null) }
            source.resume.complete(Unit)
            advanceUntilIdle()

            assertThat(waiting.await().getOrNull()).isEqualTo(MqResult.Failure(MqError.Unauthorized))
        }

    @Test
    fun `R51 signing out and in again while a request is under way never mixes the two syncs`() =
        runTest(dispatcher) {
            fillSource()
            signIn()
            val repository = repository()
            // The fourth request (tracks@500) is a blocking HTTP call already sent: cancelling does not stop it.
            source.pauseAt = 4
            source.pauseIgnoresCancel = true
            // The next request (whichever sync makes it) waits too, so the wipe lands while a sync is mid-way.
            source.holdAt = 5
            repository.start()
            source.paused.await()

            accountStore.clear()
            runCurrent()
            signIn()
            val second = async { repository.sync() }
            runCurrent()
            source.resume.complete(Unit)
            runCurrent()
            source.release.complete(Unit)
            advanceUntilIdle()

            assertThat(second.await()).isEqualTo(MqResult.Success(Unit))
            assertThat(count("track")).isEqualTo(1_200)
            assertThat(count("album")).isEqualTo(2)
            STAGING_TABLES.forEach { assertThat(count(it)).isEqualTo(0) }
        }

    @Test
    fun `a playlist that is gone or hidden is kept empty and does not block the sync`() =
        runTest(dispatcher) {
            fillSource(trackCount = 10)
            source.playlistErrors = mapOf("p1" to MqError.NotFound)
            signIn()

            assertThat(repository().sync()).isEqualTo(MqResult.Success(Unit))
            assertThat(count("playlist")).isEqualTo(2)
            assertThat(count("playlist_entry")).isEqualTo(0)
        }

    @Test
    fun `a long playlist is read page by page`() =
        runTest(dispatcher) {
            fillSource(trackCount = 600)
            source.entries = mapOf(id("p1") to List(600) { PlaylistEntry("e$it", id("t$it")) })
            signIn()

            repository().sync()

            assertThat(source.calls).containsAtLeast("entries:p1@0", "entries:p1@500").inOrder()
            assertThat(database.rows("SELECT MAX(position), COUNT(*) FROM playlist_entry"))
                .containsExactly(listOf("599", "600"))
        }

    @Test
    fun `a sync interrupted during playlist entries resumes at that playlist`() =
        runTest(dispatcher) {
            fillSource(trackCount = 10)
            signIn()
            val firstLife = CoroutineScope(backgroundScope.coroutineContext + Job())
            source.pauseAt = 6 // albumArtists, albums, tracks, playlists, entries:p1, then entries:p2
            firstLife.launch { repository(firstLife).sync() }
            source.paused.await()
            firstLife.cancel()
            source.calls.clear()
            source.pauseAt = null

            repository().sync()

            assertThat(source.calls).containsExactly("entries:p2@0")
            assertThat(count("playlist_entry")).isEqualTo(3)
        }

    @Test
    fun `an empty library syncs to an empty library`() =
        runTest(dispatcher) {
            signIn()
            val repository = repository()

            assertThat(repository.sync()).isEqualTo(MqResult.Success(Unit))
            assertThat(count("album")).isEqualTo(0)
            assertThat(repository.status.value).isEqualTo(SyncStatus.Idle(clock.now))
        }

    @Test
    fun `a step that came back short is fetched once more`() =
        runTest(dispatcher) {
            fillSource(trackCount = 10)
            source.shortListings = setOf("albums")
            signIn()

            assertThat(repository().sync()).isEqualTo(MqResult.Success(Unit))
            assertThat(source.calls.count { it == "albums@0" }).isEqualTo(2)
            assertThat(count("album")).isEqualTo(2)
        }

    @Test
    fun `a checkpoint with an unknown step starts over instead of crashing`() =
        runTest(dispatcher) {
            fillSource(trackCount = 10)
            signIn()
            database.syncDao().saveState(
                SyncStateEntity(
                    checkpointServerId = "s",
                    checkpointUserId = "user-1",
                    checkpointStartedAtMillis = clock.now.toEpochMilliseconds(),
                    checkpointStep = "RenamedStep",
                ),
            )

            assertThat(repository().sync()).isEqualTo(MqResult.Success(Unit))
            assertThat(source.calls.first()).isEqualTo("albumArtists@0")
        }

    @Test
    fun `another account never sees the previous library`() =
        runTest(dispatcher) {
            fillSource(trackCount = 10)
            signIn()
            val repository = repository()
            repository.sync()
            source.tracks = source.tracks.take(3)
            source.calls.clear()
            repository.start()

            accountStore.save(
                StoredAccount("http://nas:8096", server, "Home", "user-2", "Bob", accessToken = "token-2"),
            )

            repository.status.first { it is SyncStatus.Idle && source.calls.isNotEmpty() }
            assertThat(count("track")).isEqualTo(3)
            assertThat(database.syncDao().state()?.ownerUserId).isEqualTo("user-2")
        }

    @Test
    fun `a 401 during an automatic sync signs out with session expired and wipes the library`() =
        runTest(dispatcher) {
            fillSource(trackCount = 10)
            signIn()
            val sessions =
                DefaultSessionRepository(
                    io.github.kentprodan.mediatheq.core.testing
                        .FakeAuthSource(),
                    accountStore,
                    io.github.kentprodan.mediatheq.core.testing
                        .FakeQueueRepository(),
                    dispatcher,
                )
            val repository =
                DefaultLibrarySyncRepository(
                    source,
                    accountStore,
                    database.syncDao(),
                    sessions,
                    clock,
                    backgroundScope,
                    userData(backgroundScope),
                )
            source.failAt = 3
            source.failure = MqError.Unauthorized

            repository.start()

            sessions.session.first { it == SessionState.LoggedOut(LogoutReason.SessionExpired) }
            repository.status.first { it == SyncStatus.Idle(null) }
            assertThat(count("track_staging")).isEqualTo(0)
            assertThat(count("sync_state")).isEqualTo(0)
        }
}

private val STAGING_TABLES =
    listOf(
        "artist_staging",
        "album_staging",
        "album_artist_staging",
        "track_staging",
        "track_artist_staging",
        "playlist_staging",
        "playlist_entry_staging",
    )

/** Every library table a sign-out empties (issue #51). */
private val ALL_TABLES =
    STAGING_TABLES +
        listOf(
            "artist",
            "album",
            "album_artist",
            "track",
            "track_artist",
            "playlist",
            "playlist_entry",
            "search",
            "sync_state",
        )
