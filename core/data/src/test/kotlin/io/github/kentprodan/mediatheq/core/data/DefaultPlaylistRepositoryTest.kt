package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.PlaylistItem.Companion.PENDING_ENTRY_ID
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySource
import io.github.kentprodan.mediatheq.core.testing.FakePlaylistEditor
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class DefaultPlaylistRepositoryTest {
    private val server = ServerId("s")
    private val dispatcher = StandardTestDispatcher()
    private val database = inMemoryDatabase(dispatcher)
    private val dao = database.playlistDao()
    private val accountStore = AccountStore(InMemoryPreferencesDataStore(), FakeTokenCipher())
    private val session = FakeSessionRepository()
    private val source = FakeLibrarySource()
    private val editor = FakePlaylistEditor()
    private val playlistId = MediaId(server, "p1")

    private fun id(value: String) = MediaId(server, value)

    private fun TestScope.repository() =
        DefaultPlaylistRepository(dao, source, editor, accountStore, session, backgroundScope)

    @After
    fun close() = database.close()

    /** Tracks t1..t4 of 60 s each; playlist p1 holds t1, a track not in the library, t2 and t3. */
    @Before
    fun library() =
        runTest(dispatcher) {
            accountStore.save(StoredAccount("http://nas:8096", server, "Home", "user-1", "Ana", accessToken = "token"))
            val syncDao = database.syncDao()
            syncDao.stageTracks(
                (1..4).map {
                    TrackStaging(
                        TrackEntity("s", "t$it", "Track $it", "Track $it", durationMillis = 60_000),
                    )
                },
            )
            syncDao.swap(1, "s", "user-1")
            dao.replace(
                PlaylistEntity("s", "p1", "Drum", "Drum", trackCount = 4, durationMillis = 180_000),
                listOf("t1", "hidden", "t2", "t3").mapIndexed { i, t -> PlaylistEntryEntity("s", "p1", i, "e-$t", t) },
            )
            // What the server answers when the playlist is read back after an edit.
            source.playlists = listOf(Playlist(playlistId, "Drum (server)", trackCount = 9))
            source.entries = mapOf(playlistId to listOf(PlaylistEntry("e-t4", id("t4"))))
        }

    private suspend fun entryIds() = dao.entryRows("s", "p1").map { it.entryId }

    private suspend fun DefaultPlaylistRepository.visible() = items(playlistId).first()

    @Test
    fun `items skip tracks not in the library but keep server positions`() =
        runTest(dispatcher) {
            val items = repository().visible()

            assertThat(items.map { it.track.id.itemId to it.position })
                .containsExactly("t1" to 0, "t2" to 2, "t3" to 3)
                .inOrder()
        }

    @Test
    fun `an add shows pending entries and the new count before the server answers`() =
        runTest(dispatcher) {
            editor.gate = CompletableDeferred()
            val repository = repository()

            val add = async { repository.add(playlistId, listOf(id("t4"), id("t1"))) }
            runCurrent()

            assertThat(
                entryIds(),
            ).containsExactly("e-t1", "e-hidden", "e-t2", "e-t3", PENDING_ENTRY_ID, PENDING_ENTRY_ID)
            assertThat(repository.visible().filter { it.isPending }.map { it.track.id.itemId })
                .containsExactly("t4", "t1")
                .inOrder()
            val row = dao.playlistRow("s", "p1")!!
            assertThat(row.trackCount).isEqualTo(6)
            assertThat(row.durationMillis).isEqualTo(300_000)

            editor.gate!!.complete(Unit)
            assertThat(add.await()).isEqualTo(MqResult.Success(Unit))
            assertThat(editor.calls).containsExactly("add:p1:t4,t1")
        }

    @Test
    fun `a successful edit reads the playlist back for real entry ids`() =
        runTest(dispatcher) {
            repository().add(playlistId, listOf(id("t4")))

            assertThat(entryIds()).containsExactly("e-t4")
            assertThat(dao.playlistRow("s", "p1")?.name).isEqualTo("Drum (server)")
        }

    @Test
    fun `a failed add is undone`() =
        runTest(dispatcher) {
            editor.failure = MqError.Network

            val result = repository().add(playlistId, listOf(id("t4")))

            assertThat(result).isEqualTo(MqResult.Failure(MqError.Network))
            assertThat(entryIds()).containsExactly("e-t1", "e-hidden", "e-t2", "e-t3").inOrder()
            assertThat(dao.playlistRow("s", "p1")?.trackCount).isEqualTo(4)
            assertThat(source.calls).isEmpty()
        }

    @Test
    fun `R6 the playlist cover is read again after the collage delay`() =
        runTest(dispatcher) {
            val repository = repository()
            repository.add(playlistId, listOf(id("t4")))
            assertThat(dao.playlistRow("s", "p1")?.image?.tag).isNull()

            source.playlists = listOf(Playlist(playlistId, "Drum", artwork = Artwork("p1", "collage", null)))
            advanceTimeBy(1_900.milliseconds)
            assertThat(dao.playlistRow("s", "p1")?.image?.tag).isNull()
            advanceTimeBy(200.milliseconds)

            assertThat(dao.playlistRow("s", "p1")?.image?.tag).isEqualTo("collage")
        }

    @Test
    fun `remove sends entry ids and counts the rest at once`() =
        runTest(dispatcher) {
            editor.gate = CompletableDeferred()
            val repository = repository()

            val remove = async { repository.remove(playlistId, setOf("e-t1", "e-t3")) }
            runCurrent()

            assertThat(entryIds()).containsExactly("e-hidden", "e-t2").inOrder()
            assertThat(dao.playlistRow("s", "p1")?.trackCount).isEqualTo(2)
            editor.gate!!.complete(Unit)
            remove.await()
            assertThat(editor.calls.single()).isEqualTo("remove:p1:e-t1,e-t3")
        }

    @Test
    fun `a failed remove is undone`() =
        runTest(dispatcher) {
            editor.failure = MqError.Server(500)

            repository().remove(playlistId, setOf("e-t1"))

            assertThat(entryIds()).containsExactly("e-t1", "e-hidden", "e-t2", "e-t3").inOrder()
        }

    @Test
    fun `move with a hidden entry sends the server index`() =
        runTest(dispatcher) {
            editor.failure = MqError.Network // keep the local result instead of the read back
            editor.gate = CompletableDeferred()
            val repository = repository()
            val (t1, _, t3) = repository.visible()

            val move = async { repository.move(playlistId, t3, t1) }
            runCurrent()

            assertThat(editor.calls).containsExactly("move:p1:e-t3:0")
            assertThat(entryIds()).containsExactly("e-t3", "e-t1", "e-hidden", "e-t2").inOrder()
            editor.gate!!.complete(Unit)
            move.await()
        }

    @Test
    fun `move down lands where the target was`() =
        runTest(dispatcher) {
            val repository = repository()
            val (t1, _, t3) = repository.visible()
            editor.failure = MqError.Network
            editor.gate = CompletableDeferred()

            val move = async { repository.move(playlistId, t1, t3) }
            runCurrent()

            assertThat(editor.calls).containsExactly("move:p1:e-t1:3")
            assertThat(entryIds()).containsExactly("e-hidden", "e-t2", "e-t3", "e-t1").inOrder()
            editor.gate!!.complete(Unit)
            move.await()
        }

    @Test
    fun `a move of an item that is no longer there does nothing`() =
        runTest(dispatcher) {
            val repository = repository()
            val (t1, t2) = repository.visible()

            val result = repository.move(playlistId, t2.copy(entryId = "e-gone"), t1)

            assertThat(result).isEqualTo(MqResult.Failure(MqError.NotFound))
            assertThat(editor.calls).isEmpty()
        }

    @Test
    fun `a failed rename is undone`() =
        runTest(dispatcher) {
            editor.failure = MqError.Forbidden

            assertThat(repository().rename(playlistId, "Nou")).isEqualTo(MqResult.Failure(MqError.Forbidden))

            assertThat(dao.playlistRow("s", "p1")?.name).isEqualTo("Drum")
        }

    @Test
    fun `R5 delete never asks the server about an id that is not a stored playlist`() =
        runTest(dispatcher) {
            val result = repository().delete(id("t1"))

            assertThat(result).isEqualTo(MqResult.Failure(MqError.NotFound))
            assertThat(editor.calls).isEmpty()
        }

    @Test
    fun `delete removes the playlist, and a failed one puts it back`() =
        runTest(dispatcher) {
            val repository = repository()
            editor.failure = MqError.Network

            repository.delete(playlistId)
            assertThat(entryIds()).hasSize(4)

            editor.failure = null
            assertThat(repository.delete(playlistId)).isEqualTo(MqResult.Success(Unit))
            assertThat(repository.playlist(playlistId).first()).isNull()
            assertThat(editor.calls).containsExactly("delete:p1", "delete:p1")
        }

    @Test
    fun `create makes the playlist then adds its tracks`() =
        runTest(dispatcher) {
            editor.createdId = "p9"
            source.playlists = listOf(Playlist(id("p9"), "Nou", trackCount = 1))
            source.entries = mapOf(id("p9") to listOf(PlaylistEntry("e-t2", id("t2"))))

            val result = repository().create("Nou", listOf(id("t2")))

            assertThat(result).isEqualTo(MqResult.Success(id("p9")))
            assertThat(editor.calls).containsExactly("create:Nou", "add:p9:t2").inOrder()
            assertThat(dao.entryRows("s", "p9").map { it.entryId }).containsExactly("e-t2")
        }

    @Test
    fun `a 401 ends the session`() =
        runTest(dispatcher) {
            editor.failure = MqError.Unauthorized

            repository().rename(playlistId, "Nou")

            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut(LogoutReason.SessionExpired))
        }
}
