package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.database.entity.UserDataColumns
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlaybackReport
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackSource
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataSink
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class DefaultUserDataRepositoryTest {
    private val server = ServerId("s")
    private val dispatcher = StandardTestDispatcher()
    private val database = inMemoryDatabase(dispatcher)
    private val dao = database.userDataDao()
    private val accountStore = AccountStore(InMemoryPreferencesDataStore(), FakeTokenCipher())
    private val session = FakeSessionRepository()
    private val sink = FakeUserDataSink()
    private val track = MediaId(server, "t1")
    private val album = MediaId(server, "al1")

    private fun TestScope.repository() = DefaultUserDataRepository(dao, sink, accountStore, session, backgroundScope)

    private suspend fun stored(id: MediaId) = dao.userData(id.serverId.value, id.itemId)

    @After
    fun close() = database.close()

    @Before
    fun library() =
        runTest(dispatcher) {
            accountStore.save(StoredAccount("http://nas:8096", server, "Home", "user-1", "Ana", accessToken = "token"))
            val syncDao = database.syncDao()
            syncDao.stageAlbums(listOf(AlbumStaging(AlbumEntity("s", "al1", "Underground", "Underground"))))
            syncDao.stageTracks(
                listOf(TrackStaging(TrackEntity("s", "t1", "Pe-o margine", "Pe-o margine", userData = rated(6.0)))),
            )
            syncDao.swap(1, "s", "user-1")
        }

    private fun rated(rating: Double?) = UserDataColumns(rating = rating, playCount = 3)

    @Test
    fun `a favorite shows in Room before the server answers, then stays`() =
        runTest(dispatcher) {
            val repository = repository()
            sink.gate = CompletableDeferred()

            val result = async { repository.setFavorite(track, true) }
            runCurrent()
            assertThat(stored(track)?.isFavorite).isTrue()

            sink.gate!!.complete(Unit)
            assertThat(result.await()).isEqualTo(MqResult.Success(Unit))
            assertThat(stored(track)).isEqualTo(rated(6.0).copy(isFavorite = true))
            assertThat(sink.calls).containsExactly("favorite:t1:true")
        }

    @Test
    fun `a failed favorite is undone and returned`() =
        runTest(dispatcher) {
            sink.failure = MqError.Server(500)

            assertThat(repository().setFavorite(album, true)).isEqualTo(MqResult.Failure(MqError.Server(500)))
            assertThat(stored(album)?.isFavorite).isFalse()
        }

    @Test
    fun `stars are sent as the server rating and clearing sends null`() =
        runTest(dispatcher) {
            val repository = repository()

            repository.setRating(track, 8)
            assertThat(stored(track)?.rating).isEqualTo(8.0)

            repository.setRating(track, null)
            assertThat(stored(track)?.rating).isNull()
            assertThat(sink.calls).containsExactly("rating:t1:8.0", "rating:t1:null").inOrder()
        }

    @Test
    fun `a refused change is offered as the app-wide error until dismissed`() =
        runTest(dispatcher) {
            val repository = repository()
            sink.failure = MqError.Forbidden

            repository.setFavorite(track, true)
            assertThat(repository.error.value).isEqualTo(MqError.Forbidden)

            repository.dismissError()
            assertThat(repository.error.value).isNull()
        }

    @Test
    fun `a 401 is not an app-wide error, signing out says it`() =
        runTest(dispatcher) {
            val repository = repository()
            sink.failure = MqError.Unauthorized

            repository.setFavorite(track, true)

            assertThat(repository.error.value).isNull()
        }

    @Test
    fun `a failed rating puts back only the rating`() =
        runTest(dispatcher) {
            val repository = repository()
            repository.setFavorite(track, true)
            sink.failure = MqError.Server(500)

            assertThat(repository.setRating(track, 10)).isEqualTo(MqResult.Failure(MqError.Server(500)))
            assertThat(stored(track)).isEqualTo(rated(6.0).copy(isFavorite = true))
        }

    @Test
    fun `a 401 undoes the change and signs out as expired`() =
        runTest(dispatcher) {
            sink.failure = MqError.Unauthorized

            repository().setFavorite(track, true)

            assertThat(stored(track)?.isFavorite).isFalse()
            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut(LogoutReason.SessionExpired))
        }

    @Test
    fun `an item not in the library is not sent`() =
        runTest(dispatcher) {
            assertThat(repository().setFavorite(MediaId(server, "gone"), true))
                .isEqualTo(MqResult.Failure(MqError.NotFound))
            assertThat(sink.calls).isEmpty()
        }

    @Test
    fun `signed out, nothing is sent`() =
        runTest(dispatcher) {
            accountStore.clear()

            assertThat(repository().setFavorite(track, true)).isEqualTo(MqResult.Failure(MqError.Unauthorized))
            assertThat(sink.calls).isEmpty()
        }

    @Test
    fun `a stale server echo during a write keeps the new value`() =
        runTest(dispatcher) {
            val repository = repository()
            sink.gate = CompletableDeferred()
            val write = async { repository.setFavorite(track, true) }
            runCurrent()

            repository.applyFromServer(mapOf(track to UserData(isFavorite = false, rating = 6.0, playCount = 4)))

            assertThat(stored(track)).isEqualTo(UserDataColumns(isFavorite = true, rating = 6.0, playCount = 4))
            sink.gate!!.complete(Unit)
            write.await()
        }

    @Test
    fun `changes from another client are written`() =
        runTest(dispatcher) {
            repository().applyFromServer(mapOf(album to UserData(isFavorite = true, rating = 10.0)))

            assertThat(stored(album)).isEqualTo(UserDataColumns(isFavorite = true, rating = 10.0))
        }

    @Test
    fun `a sync swap during a write keeps the new value`() =
        runTest(dispatcher) {
            val repository = repository()
            val syncDao = database.syncDao()
            sink.gate = CompletableDeferred()
            val write = async { repository.setRating(track, 4) }
            runCurrent()
            // A sync fetched the track before the change and stages it now, then swaps.
            syncDao.stageTracks(listOf(TrackStaging(TrackEntity("s", "t1", "Pe-o margine", "Pe-o margine"))))
            syncDao.swap(2, "s", "user-1")

            repository.reapplyOverrides()

            assertThat(stored(track)?.rating).isEqualTo(4.0)
            sink.gate!!.complete(Unit)
            write.await()
        }

    @Test
    fun `R2 a favorite set while the track plays survives progress and stop reports`() =
        runTest(dispatcher) {
            val userData = repository()
            val source = FakePlaybackSource()
            val playback = DefaultPlaybackRepository(source, accountStore, session, userData)
            // Jellyfin 12 writes the user data it had when the play started back with each progress and stop report.
            var atStart = UserData()
            source.onReport = { kind, report ->
                val id = report.stream.itemId
                if (kind == "start") atStart = sink.serverState[id] ?: UserData()
                if (kind != "start") sink.serverState[id] = atStart
            }
            val report = PlaybackReport(FakePlaybackSource.fakeStream(track), 0.seconds, isPaused = false)

            playback.reportStart(report)
            userData.setFavorite(track, true)
            userData.setRating(track, 10)
            playback.reportProgress(report.copy(position = 10.seconds))
            runCurrent()
            assertThat(sink.serverState[track]?.isFavorite).isTrue()
            playback.reportStopped(report.copy(position = 20.seconds), failed = false)
            runCurrent()

            assertThat(sink.serverState[track]).isEqualTo(UserData(isFavorite = true, rating = 10.0))
            assertThat(stored(track)?.isFavorite).isTrue()
        }

    @Test
    fun `R2 after the stop report the change is not sent again`() =
        runTest(dispatcher) {
            val userData = repository()
            val playback = DefaultPlaybackRepository(FakePlaybackSource(), accountStore, session, userData)
            val report = PlaybackReport(FakePlaybackSource.fakeStream(track), 0.seconds, isPaused = false)
            playback.reportStart(report)
            userData.setFavorite(track, true)
            playback.reportStopped(report, failed = false)
            runCurrent()
            sink.calls.clear()

            playback.reportStart(report)
            playback.reportProgress(report)
            playback.reportStopped(report, failed = false)
            runCurrent()

            assertThat(sink.calls).isEmpty()
        }

    @Test
    fun `changes to items that are not playing are sent once`() =
        runTest(dispatcher) {
            val userData = repository()
            val playback = DefaultPlaybackRepository(FakePlaybackSource(), accountStore, session, userData)
            val report = PlaybackReport(FakePlaybackSource.fakeStream(track), 0.seconds, isPaused = false)
            playback.reportStart(report)

            userData.setFavorite(album, true)
            playback.reportProgress(report)
            playback.reportStopped(report, failed = false)
            runCurrent()

            assertThat(sink.calls).containsExactly("favorite:al1:true")
        }

    @Test
    fun `a change confirmed while a sync fetches survives its swap`() =
        runTest(dispatcher) {
            val repository = repository()
            val syncDao = database.syncDao()
            repository.onSyncStarted()
            // The sync read the track before the change...
            val oldPage = TrackStaging(TrackEntity("s", "t1", "Pe-o margine", "Pe-o margine", userData = rated(6.0)))

            repository.setFavorite(track, true)
            // ...and stages it only after the server confirmed the change.
            syncDao.stageTracks(listOf(oldPage))
            syncDao.swap(2, "s", "user-1")
            repository.reapplyOverrides()

            assertThat(stored(track)?.isFavorite).isTrue()
        }

    @Test
    fun `a change confirmed before a sync started is not written over it`() =
        runTest(dispatcher) {
            val repository = repository()
            val syncDao = database.syncDao()
            repository.setFavorite(track, true)
            repository.onSyncStarted()
            // Another client unfavorited it meanwhile; this sync read that.
            syncDao.stageTracks(listOf(TrackStaging(TrackEntity("s", "t1", "Pe-o margine", "Pe-o margine"))))
            syncDao.swap(2, "s", "user-1")

            repository.reapplyOverrides()

            assertThat(stored(track)?.isFavorite).isFalse()
        }

    @Test
    fun `signing out forgets pending changes, so the next user gets none of them`() =
        runTest(dispatcher) {
            val userData = repository()
            val playback = DefaultPlaybackRepository(FakePlaybackSource(), accountStore, session, userData)
            val report = PlaybackReport(FakePlaybackSource.fakeStream(track), 0.seconds, isPaused = false)
            playback.reportStart(report)
            userData.setFavorite(track, true)
            sink.calls.clear()

            userData.forgetPending()
            playback.reportStopped(report, failed = false)
            runCurrent()

            assertThat(sink.calls).isEmpty()
        }

    private suspend fun waiting() = dao.pendingChanges("s", "user-1").map { it.itemId }

    @Test
    fun `offline a favorite stays, with no message, and waits for the server`() =
        runTest(dispatcher) {
            val repository = repository()
            sink.failure = MqError.Network

            assertThat(repository.setFavorite(album, true)).isEqualTo(MqResult.Success(Unit))

            assertThat(stored(album)?.isFavorite).isTrue()
            assertThat(repository.error.value).isNull()
            assertThat(waiting()).containsExactly("al1")
        }

    @Test
    fun `waiting changes are sent oldest first once the server is back`() =
        runTest(dispatcher) {
            val repository = repository()
            sink.failure = MqError.Network
            repository.setFavorite(album, true)
            repository.setRating(track, 8)
            sink.failure = null
            sink.calls.clear()

            repository.flushPending()

            assertThat(sink.calls).containsExactly("favorite:al1:true", "rating:t1:8.0").inOrder()
            assertThat(waiting()).isEmpty()
        }

    @Test
    fun `the last offline change to an item wins`() =
        runTest(dispatcher) {
            val repository = repository()
            sink.failure = MqError.Network
            repository.setFavorite(track, true)
            repository.setRating(track, 4)
            repository.setFavorite(track, false)
            repository.setRating(track, 8)
            sink.failure = null
            sink.calls.clear()

            repository.flushPending()

            assertThat(sink.calls).containsExactly("favorite:t1:false", "rating:t1:8.0").inOrder()
            assertThat(stored(track)).isEqualTo(rated(8.0))
        }

    @Test
    fun `a waiting change the server refuses is undone with the message`() =
        runTest(dispatcher) {
            val repository = repository()
            sink.failure = MqError.Network
            repository.setFavorite(album, true)
            sink.failure = MqError.Forbidden

            repository.flushPending()

            assertThat(stored(album)?.isFavorite).isFalse()
            assertThat(repository.error.value).isEqualTo(MqError.Forbidden)
            assertThat(waiting()).isEmpty()
        }

    @Test
    fun `a half refused change undoes only the refused part`() =
        runTest(dispatcher) {
            val repository = repository()
            sink.failure = MqError.Network
            repository.setFavorite(track, true)
            repository.setRating(track, 10)
            sink.failure = null
            sink.ratingFailure = MqError.Forbidden

            repository.flushPending()

            assertThat(stored(track)).isEqualTo(rated(6.0).copy(isFavorite = true))
            assertThat(sink.serverState[track]?.isFavorite).isTrue()
        }

    @Test
    fun `losing the network midway keeps the rest waiting`() =
        runTest(dispatcher) {
            val repository = repository()
            sink.failure = MqError.Network
            repository.setFavorite(album, true)
            repository.setRating(track, 8)
            sink.failure = null
            sink.ratingFailure = MqError.Network

            repository.flushPending()

            assertThat(sink.serverState[album]?.isFavorite).isTrue()
            assertThat(waiting()).containsExactly("t1")
            assertThat(stored(track)?.rating).isEqualTo(8.0)
            assertThat(repository.error.value).isNull()
        }

    @Test
    fun `a change made while its item is being sent stays waiting and goes next`() =
        runTest(dispatcher) {
            val repository = repository()
            sink.failure = MqError.Network
            repository.setFavorite(track, true)
            sink.failure = null
            sink.gate = CompletableDeferred()
            val flush = async { repository.flushPending() }
            runCurrent()

            val rating = async { repository.setRating(track, 8) }
            runCurrent()
            sink.gate!!.complete(Unit)
            flush.await()

            assertThat(rating.await()).isEqualTo(MqResult.Success(Unit))
            assertThat(sink.serverState[track]).isEqualTo(UserData(isFavorite = true, rating = 8.0))
            assertThat(waiting()).isEmpty()
        }

    @Test
    fun `waiting changes survive a restart and its sync, then are sent`() =
        runTest(dispatcher) {
            sink.failure = MqError.Network
            repository().setFavorite(track, true)
            sink.failure = null
            sink.calls.clear()

            // The app starts again: a new repository over the same database, then a sync fetched before the change.
            val restarted = repository()
            restarted.onSyncStarted()
            database.syncDao().stageTracks(
                listOf(TrackStaging(TrackEntity("s", "t1", "Pe-o margine", "Pe-o margine", userData = rated(6.0)))),
            )
            database.syncDao().swap(2, "s", "user-1")
            restarted.reapplyOverrides()
            assertThat(stored(track)?.isFavorite).isTrue()

            restarted.flushPending()
            assertThat(sink.calls).containsExactly("favorite:t1:true")
        }

    @Test
    fun `waiting changes of another account are not sent`() =
        runTest(dispatcher) {
            val repository = repository()
            sink.failure = MqError.Network
            repository.setFavorite(track, true)
            sink.failure = null
            sink.calls.clear()
            accountStore.save(StoredAccount("http://nas:8096", server, "Home", "user-2", "Ion", accessToken = "other"))

            repository.flushPending()

            assertThat(sink.calls).isEmpty()
        }

    @Test
    fun `a stop report does not wait for a slow write`() =
        runTest(dispatcher) {
            val userData = repository()
            val playback = DefaultPlaybackRepository(FakePlaybackSource(), accountStore, session, userData)
            val report = PlaybackReport(FakePlaybackSource.fakeStream(track), 0.seconds, isPaused = false)
            playback.reportStart(report)
            userData.setFavorite(track, true)
            sink.gate = CompletableDeferred()
            val slowWrite = async { userData.setRating(album, 6) }
            runCurrent()

            val stopped = async { playback.reportStopped(report, failed = false) }
            runCurrent()

            assertThat(stopped.isCompleted).isTrue()
            sink.gate!!.complete(Unit)
            slowWrite.await()
        }
}
