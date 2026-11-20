package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.PlaylistItem.Companion.PENDING_ENTRY_ID
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistChange
import io.github.kentprodan.mediatheq.core.model.PlaylistConflict
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.model.PlaylistResolution
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySource
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import io.github.kentprodan.mediatheq.core.testing.FakePlaylistEditor
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import io.github.kentprodan.mediatheq.core.testing.TestClock
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

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
    private val preferences = FakeLibraryPreferencesRepository()
    private val downloads = FakeDownloadRepository()
    private val filter = OfflineFilter(preferences, FakeNetworkMonitor()) { downloads }
    private val clock = TestClock()

    private fun id(value: String) = MediaId(server, value)

    private fun TestScope.repository() =
        DefaultPlaylistRepository(dao, source, editor, accountStore, session, backgroundScope, filter, clock)

    @After
    fun close() = database.close()

    /** Tracks t1..t4 of 60 s each (t1 on album al1); playlist p1 holds t1, a track not in the library, t2 and t3. */
    @Before
    fun library() =
        runTest(dispatcher) {
            accountStore.save(StoredAccount("http://nas:8096", server, "Home", "user-1", "Ana", accessToken = "token"))
            val syncDao = database.syncDao()
            syncDao.stageAlbums(listOf(AlbumStaging(AlbumEntity("s", "al1", "A", "A", normalizationGainDb = -7.5))))
            syncDao.stageTracks(
                (1..4).map {
                    TrackStaging(
                        TrackEntity(
                            "s",
                            "t$it",
                            "Track $it",
                            "Track $it",
                            albumId = "al1".takeIf { _ -> it == 1 },
                            durationMillis = 60_000,
                        ),
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
    fun `in offline mode items are only the downloaded tracks, at their server positions`() =
        runTest(dispatcher) {
            downloads.downloads.value =
                listOf(TrackDownload(Track(id("t2"), "Track 2", emptyList(), 1.minutes), DownloadState.Completed))
            preferences.setOfflineMode(true)

            val items = repository().visible()

            assertThat(items.map { it.track.id.itemId to it.position }).containsExactly("t2" to 2)
        }

    @Test
    fun `items skip tracks not in the library but keep server positions`() =
        runTest(dispatcher) {
            val items = repository().visible()

            assertThat(items.map { it.track.id.itemId to it.position })
                .containsExactly("t1" to 0, "t2" to 2, "t3" to 3)
                .inOrder()
        }

    @Test
    fun `items carry their album's normalization gain`() =
        runTest(dispatcher) {
            val gains = repository().visible().map { it.track.albumNormalizationGainDb }

            assertThat(gains).containsExactly(-7.5, null, null).inOrder()
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
    fun `an add that cannot reach the server stays here and waits for it (#417)`() =
        runTest(dispatcher) {
            editor.failure = MqError.Network

            val result = repository().add(playlistId, listOf(id("t4")))

            assertThat(result).isEqualTo(MqResult.Success(Unit))
            assertThat(dao.playlistRow("s", "p1")?.trackCount).isEqualTo(5)
            assertThat(dao.pendingEdits("s", "user-1").map { it.kind }).containsExactly("Add")
        }

    /** The server still has the playlist as the phone last knew it (no conflict). */
    private fun serverUnchanged() {
        source.playlists = listOf(Playlist(playlistId, "Drum", trackCount = 4))
        source.entries =
            mapOf(playlistId to listOf("t1", "hidden", "t2", "t3").map { PlaylistEntry("e-$it", id(it)) })
    }

    /** An add of t4 made offline, then the server back with [serverUnchanged] unless the test changes it. */
    private suspend fun TestScope.offlineAdd(): DefaultPlaylistRepository {
        editor.failure = MqError.Network
        val repository = repository()
        repository.add(playlistId, listOf(id("t4")))
        editor.failure = null
        editor.calls.clear()
        serverUnchanged()
        return repository
    }

    @Test
    fun `waiting edits go in order when the server did not change the playlist (#417)`() =
        runTest(dispatcher) {
            val repository = offlineAdd()
            // Queued behind the add without asking the server.
            repository.remove(playlistId, setOf("e-t1"))
            assertThat(dao.pendingEdits("s", "user-1").map { it.kind }).containsExactly("Add", "Remove").inOrder()

            repository.flushPending()

            assertThat(editor.calls).containsExactly("add:p1:t4", "remove:p1:e-t1").inOrder()
            assertThat(dao.pendingEdits("s", "user-1")).isEmpty()
            assertThat(repository.conflicts.value).isEmpty()
        }

    @Test
    fun `a playlist the server changed meanwhile waits for the owner with both changes (#417)`() =
        runTest(dispatcher) {
            val repository = offlineAdd()
            source.playlists = listOf(Playlist(playlistId, "Drum (server)", trackCount = 1))
            source.entries = mapOf(playlistId to listOf(PlaylistEntry("e-t2", id("t2"))))

            repository.flushPending()

            assertThat(editor.calls).isEmpty()
            assertThat(repository.conflicts.value)
                .containsExactly(
                    PlaylistConflict(
                        playlistId,
                        "Drum",
                        since = clock.now,
                        server = PlaylistChange(removed = 3, renamedTo = "Drum (server)"),
                        phone = PlaylistChange(added = 1),
                    ),
                )
            // The phone's version stays on screen.
            assertThat(dao.entryRows("s", "p1").map { it.trackId })
                .containsExactly("t1", "hidden", "t2", "t3", "t4")
                .inOrder()
            assertThat(dao.pendingEdits("s", "user-1")).hasSize(1)
        }

    private suspend fun TestScope.conflicted(): DefaultPlaylistRepository {
        val repository = offlineAdd()
        source.entries = mapOf(playlistId to listOf(PlaylistEntry("e-t2", id("t2"))))
        repository.flushPending()
        return repository
    }

    @Test
    fun `merge sends the waiting edits on top of the server's version (#417)`() =
        runTest(dispatcher) {
            val repository = conflicted()

            val result = repository.resolve(playlistId, PlaylistResolution.Merge)

            assertThat(result).isEqualTo(MqResult.Success(Unit))
            assertThat(editor.calls).containsExactly("add:p1:t4")
            assertThat(dao.pendingEdits("s", "user-1")).isEmpty()
            assertThat(repository.conflicts.value).isEmpty()
        }

    @Test
    fun `replace makes the server's playlist the phone's (#417)`() =
        runTest(dispatcher) {
            val repository = conflicted()

            repository.resolve(playlistId, PlaylistResolution.Replace)

            assertThat(editor.calls).containsExactly("remove:p1:e-t2", "add:p1:t1,hidden,t2,t3,t4").inOrder()
            assertThat(dao.pendingEdits("s", "user-1")).isEmpty()
            assertThat(repository.conflicts.value).isEmpty()
        }

    @Test
    fun `a replace cut off by the network keeps the conflict and does the same again (#417)`() =
        runTest(dispatcher) {
            val repository = conflicted()
            editor.failureFor = { if (it.startsWith("add")) MqError.Network else null }

            val failed = repository.resolve(playlistId, PlaylistResolution.Replace)

            assertThat(failed).isEqualTo(MqResult.Failure(MqError.Network))
            assertThat(repository.conflicts.value).hasSize(1)
            assertThat(dao.pendingEdits("s", "user-1")).hasSize(1)
            // The server now has nothing of it; the phone's version is still the target.
            source.entries = mapOf(playlistId to emptyList())
            editor.failureFor = { null }
            editor.calls.clear()

            repository.resolve(playlistId, PlaylistResolution.Replace)

            assertThat(editor.calls).containsExactly("add:p1:t1,hidden,t2,t3,t4")
            assertThat(repository.conflicts.value).isEmpty()
        }

    @Test
    fun `skip drops the waiting edits and shows the server's version (#417)`() =
        runTest(dispatcher) {
            val repository = conflicted()

            repository.resolve(playlistId, PlaylistResolution.Skip)

            assertThat(editor.calls).isEmpty()
            assertThat(dao.pendingEdits("s", "user-1")).isEmpty()
            assertThat(repository.conflicts.value).isEmpty()
            assertThat(entryIds()).containsExactly("e-t2")
        }

    @Test
    fun `edits already sent before the network dropped are no conflict at the next replay (#417)`() =
        runTest(dispatcher) {
            val repository = offlineAdd()
            repository.rename(playlistId, "Trip")
            // The add goes (the server has t4 from then on), then the network drops before the rename.
            editor.failureFor = { call ->
                if (call.startsWith("add")) {
                    source.entries =
                        mapOf(
                            playlistId to
                                listOf("t1", "hidden", "t2", "t3", "t4").map { PlaylistEntry("e-$it", id(it)) },
                        )
                }
                if (call.startsWith("rename")) MqError.Network else null
            }
            repository.flushPending()
            assertThat(repository.conflicts.value).isEmpty()
            assertThat(dao.pendingEdits("s", "user-1").map { it.kind }).containsExactly("Rename")
            editor.failureFor = { null }
            editor.calls.clear()

            repository.flushPending()

            assertThat(repository.conflicts.value).isEmpty()
            assertThat(editor.calls).containsExactly("rename:p1:Trip")
        }

    @Test
    fun `a playlist deleted on the server drops its waiting edits with a message (#417)`() =
        runTest(dispatcher) {
            editor.failure = MqError.Network
            val repository = repository()
            repository.rename(playlistId, "Offline name")

            editor.failure = null
            editor.calls.clear()
            source.playlistErrors = mapOf("p1" to MqError.NotFound)
            repository.flushPending()

            assertThat(editor.calls).isEmpty()
            assertThat(dao.pendingEdits("s", "user-1")).isEmpty()
            assertThat(repository.droppedEdits.value)
                .containsExactly(DroppedPlaylistEdits("Offline name", MqError.NotFound))
            repository.acknowledgeDroppedEdits()
            assertThat(repository.droppedEdits.value).isEmpty()
        }

    @Test
    fun `a playlist the user may no longer edit drops its waiting edits with a message (#417)`() =
        runTest(dispatcher) {
            val repository = offlineAdd()
            editor.failure = MqError.Forbidden

            repository.flushPending()

            assertThat(dao.pendingEdits("s", "user-1")).isEmpty()
            assertThat(repository.droppedEdits.value).containsExactly(DroppedPlaylistEdits("Drum", MqError.Forbidden))
        }

    @Test
    fun `no network at the replay keeps the edits for next time (#417)`() =
        runTest(dispatcher) {
            val repository = offlineAdd()
            source.playlistErrors = mapOf("p1" to MqError.Network)

            repository.flushPending()

            assertThat(dao.pendingEdits("s", "user-1")).hasSize(1)
            assertThat(repository.droppedEdits.value).isEmpty()
        }

    @Test
    fun `a server error at the replay keeps the edits too (#417)`() =
        runTest(dispatcher) {
            val repository = offlineAdd()
            source.playlistErrors = mapOf("p1" to MqError.Server(500))

            repository.flushPending()

            assertThat(dao.pendingEdits("s", "user-1")).hasSize(1)
            assertThat(repository.droppedEdits.value).isEmpty()
        }

    @Test
    fun `a replace the server fails half way keeps the edits, the question and the phone's version (#417)`() =
        runTest(dispatcher) {
            val repository = conflicted()
            editor.failureFor = { if (it.startsWith("add")) MqError.Server(500) else null }

            val failed = repository.resolve(playlistId, PlaylistResolution.Replace)

            assertThat(failed).isEqualTo(MqResult.Failure(MqError.Server(500)))
            assertThat(dao.pendingEdits("s", "user-1")).hasSize(1)
            assertThat(repository.conflicts.value).hasSize(1)
            assertThat(dao.entryRows("s", "p1").map { it.trackId })
                .containsExactly("t1", "hidden", "t2", "t3", "t4")
                .inOrder()
        }

    @Test
    fun `a refresh while edits wait keeps the phone's version (#417)`() =
        runTest(dispatcher) {
            val repository = conflicted()

            repository.refresh(playlistId)

            assertThat(dao.entryRows("s", "p1").map { it.trackId })
                .containsExactly("t1", "hidden", "t2", "t3", "t4")
                .inOrder()
        }

    @Test
    fun `merge drops a remove and a move whose entry is gone on the server (#417)`() =
        runTest(dispatcher) {
            val repository = offlineAdd()
            editor.failure = MqError.Network
            repository.remove(playlistId, setOf("e-t1"))
            val items = repository.visible()
            repository.move(playlistId, items.first { it.entryId == "e-t3" }, items.first { it.entryId == "e-t2" })
            editor.failure = null
            source.entries = mapOf(playlistId to listOf(PlaylistEntry("e-t2", id("t2"))))
            repository.flushPending()

            repository.resolve(playlistId, PlaylistResolution.Merge)

            assertThat(editor.calls).containsExactly("add:p1:t4")
            assertThat(dao.pendingEdits("s", "user-1")).isEmpty()
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
            source.playlistErrors = mapOf("p1" to MqError.Network)

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
            source.playlistErrors = mapOf("p1" to MqError.Network)

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
    fun `create stores the new empty playlist`() =
        runTest(dispatcher) {
            editor.createdId = "p9"

            val result = repository().create("Nou")

            assertThat(result).isEqualTo(MqResult.Success(id("p9")))
            assertThat(editor.calls).containsExactly("create:Nou")
            assertThat(dao.playlistRow("s", "p9")?.name).isEqualTo("Nou")
        }

    @Test
    fun `a half failed add shows what the server really has`() =
        runTest(dispatcher) {
            // Like a second chunk failing: the server already holds part of the tracks.
            editor.failure = MqError.Server(500)

            val result = repository().add(playlistId, (1..4).map { id("t$it") })

            assertThat(result).isEqualTo(MqResult.Failure(MqError.Server(500)))
            assertThat(entryIds()).containsExactly("e-t4")
        }

    @Test
    fun `an edit finishes when its caller is cancelled`() =
        runTest(dispatcher) {
            editor.gate = CompletableDeferred()
            source.pauseAt = 2 // the read-back's entries request
            val repository = repository()

            val caller = launch { repository.remove(playlistId, setOf("e-t1")) }
            runCurrent()
            caller.cancel()
            editor.gate!!.complete(Unit)

            // The read-back starts although the screen that asked for the edit is gone.
            source.paused.await()
            source.resume.complete(Unit)
            repository.refresh(playlistId) // queued behind the edit
            assertThat(editor.calls).containsExactly("remove:p1:e-t1")
            assertThat(entryIds()).containsExactly("e-t4")
        }

    @Test
    fun `the read-back pages by server offset`() =
        runTest(dispatcher) {
            source.entries = mapOf(playlistId to (1..600).map { PlaylistEntry("e$it", id("t1")) })

            repository().refresh(playlistId)

            assertThat(source.calls).containsExactly("playlist:p1", "entries:p1@0", "entries:p1@500").inOrder()
            assertThat(entryIds()).hasSize(600)
        }

    @Test
    fun `a playlist already deleted on the server counts as deleted`() =
        runTest(dispatcher) {
            editor.failure = MqError.NotFound
            val repository = repository()

            assertThat(repository.delete(playlistId)).isEqualTo(MqResult.Success(Unit))

            assertThat(repository.playlist(playlistId).first()).isNull()
        }

    @Test
    fun `a 401 ends the session`() =
        runTest(dispatcher) {
            editor.failure = MqError.Unauthorized

            repository().rename(playlistId, "Nou")

            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut(LogoutReason.SessionExpired))
        }
}
