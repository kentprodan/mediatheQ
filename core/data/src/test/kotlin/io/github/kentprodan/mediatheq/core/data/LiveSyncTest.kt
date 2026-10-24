package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.server.LiveEvent
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySource
import io.github.kentprodan.mediatheq.core.testing.FakeLiveEvents
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataSink
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import io.github.kentprodan.mediatheq.core.testing.TestClock
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LiveSyncTest {
    private val server = ServerId("s")
    private val dispatcher = StandardTestDispatcher()
    private val database = inMemoryDatabase(dispatcher)
    private val accountStore = AccountStore(InMemoryPreferencesDataStore(), FakeTokenCipher())
    private val session = FakeSessionRepository()
    private val events = FakeLiveEvents()
    private val source = FakeLibrarySource()
    private val track = MediaId(server, "t1")

    /** A jitter of 0: every wait is exactly its base. */
    private val noJitter =
        object : Random() {
            override fun nextBits(bitCount: Int) = 0

            override fun nextDouble() = 0.5
        }

    private fun TestScope.liveSync(): LiveSync {
        val userData =
            DefaultUserDataRepository(
                database.userDataDao(),
                FakeUserDataSink(),
                accountStore,
                session,
                backgroundScope,
            )
        val librarySync =
            DefaultLibrarySyncRepository(
                source,
                accountStore,
                database.syncDao(),
                session,
                TestClock(),
                backgroundScope,
                userData,
            )
        return LiveSync(events, accountStore, session, userData, librarySync, backgroundScope).apply {
            random = noJitter
            setForeground(true)
            start()
        }
    }

    private fun syncs() = source.calls.count { it.startsWith("albumArtists@") }

    @After
    fun close() = database.close()

    @Before
    fun signIn() =
        runTest(dispatcher) {
            accountStore.save(StoredAccount("http://nas:8096", server, "Home", "user-1", "Ana", accessToken = "token"))
            database.syncDao().stageTracks(listOf(TrackStaging(TrackEntity("s", "t1", "Pe-o margine", "Pe-o margine"))))
            database.syncDao().swap(1, "s", "user-1")
        }

    @Test
    fun `connects while signed in and in the foreground`() =
        runTest(dispatcher) {
            liveSync()
            runCurrent()

            assertThat(events.open.value).isEqualTo(1)
        }

    @Test
    fun `user data from another client lands in Room`() =
        runTest(dispatcher) {
            liveSync()
            runCurrent()

            events.send(LiveEvent.UserDataChanged(mapOf(track to UserData(isFavorite = true, rating = 8.0))))
            runCurrent()

            val stored = database.userDataDao().userData("s", "t1")
            assertThat(stored?.isFavorite).isTrue()
            assertThat(stored?.rating).isEqualTo(8.0)
        }

    @Test
    fun `a dropped connection comes back after 1, 2 then 4 seconds`() =
        runTest(dispatcher) {
            liveSync()
            runCurrent()
            val waits = mutableListOf<Long>()

            repeat(3) {
                val closedAt = testScheduler.currentTime
                events.send(LiveEvent.Closed(MqError.Network))
                val before = events.opened.value
                while (events.opened.value == before) advanceTimeBy(1.milliseconds)
                // Polling overshoots by at most 1 ms.
                waits += testScheduler.currentTime - closedAt - 1
            }

            assertThat(waits).containsExactly(1_000L, 2_000L, 4_000L).inOrder()
        }

    @Test
    fun `the wait grows to 60 seconds at most`() =
        runTest(dispatcher) {
            val liveSync = liveSync()

            assertThat((0..10).map { liveSync.retryDelay(it) }.last()).isEqualTo(60.seconds)
            assertThat(liveSync.retryDelay(0)).isEqualTo(1.seconds)
        }

    @Test
    fun `jitter stays within 20 percent`() =
        runTest(dispatcher) {
            val liveSync = liveSync().apply { random = Random(42) }

            repeat(50) { assertThat(liveSync.retryDelay(2).inWholeMilliseconds).isIn(3_200L..4_800L) }
        }

    @Test
    fun `a connection that lasted resets the wait, one that dropped at once does not`() =
        runTest(dispatcher) {
            liveSync()
            runCurrent()
            events.send(LiveEvent.Closed(MqError.Network))
            advanceTimeBy(1_100.milliseconds)
            // Second connection: accepted and dropped at once, so the next wait is 2 s.
            events.send(LiveEvent.Connected)
            events.send(LiveEvent.Closed(MqError.Network))
            runCurrent()
            advanceTimeBy(1_500.milliseconds)
            assertThat(events.opened.value).isEqualTo(2)
            advanceTimeBy(600.milliseconds)
            assertThat(events.opened.value).isEqualTo(3)

            // Third connection lasts 30 s, so the next wait is back to 1 s.
            events.send(LiveEvent.Connected)
            advanceTimeBy(31.seconds)
            events.send(LiveEvent.Closed(MqError.Network))
            runCurrent()
            advanceTimeBy(1_100.milliseconds)
            assertThat(events.opened.value).isEqualTo(4)
        }

    @Test
    fun `a 401 stops reconnecting and signs out as expired`() =
        runTest(dispatcher) {
            liveSync()
            runCurrent()

            events.send(LiveEvent.Closed(MqError.Unauthorized))
            advanceTimeBy(5.minutes)

            assertThat(events.opened.value).isEqualTo(1)
            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut(LogoutReason.SessionExpired))
        }

    @Test
    fun `in the background the socket closes unless music plays`() =
        runTest(dispatcher) {
            val liveSync = liveSync()
            runCurrent()

            liveSync.setPlaying(true)
            liveSync.setForeground(false)
            runCurrent()
            assertThat(events.open.value).isEqualTo(1)

            liveSync.setPlaying(false)
            runCurrent()
            assertThat(events.open.value).isEqualTo(0)

            liveSync.setForeground(true)
            runCurrent()
            assertThat(events.open.value).isEqualTo(1)
        }

    @Test
    fun `signing out closes the socket`() =
        runTest(dispatcher) {
            liveSync()
            runCurrent()

            accountStore.clear()
            runCurrent()

            assertThat(events.open.value).isEqualTo(0)
        }

    @Test
    fun `a burst of library changes starts one sync once the server is quiet`() =
        runTest(dispatcher) {
            liveSync()
            runCurrent()

            repeat(3) {
                events.send(LiveEvent.LibraryChanged)
                advanceTimeBy(1.seconds)
            }
            assertThat(syncs()).isEqualTo(0)
            advanceTimeBy(5.seconds)

            assertThat(syncs()).isEqualTo(1)
        }

    @Test
    fun `changes that never stop still sync after 30 seconds`() =
        runTest(dispatcher) {
            liveSync()
            runCurrent()

            repeat(40) {
                events.send(LiveEvent.LibraryChanged)
                advanceTimeBy(1.seconds)
            }

            assertThat(syncs()).isAtLeast(1)
        }

    @Test
    fun `a change during a running sync syncs again afterwards`() =
        runTest(dispatcher) {
            val liveSync = liveSync()
            runCurrent()
            source.pauseAt = source.calls.size + 1
            events.send(LiveEvent.LibraryChanged)
            advanceTimeBy(6.seconds)
            source.paused.await()

            events.send(LiveEvent.LibraryChanged)
            advanceTimeBy(6.seconds)
            source.resume.complete(Unit)
            // The second change waits for the first sync, then for its own quiet time.
            advanceTimeBy(6.seconds)

            assertThat(syncs()).isEqualTo(2)
            liveSync.setForeground(false)
        }
}
