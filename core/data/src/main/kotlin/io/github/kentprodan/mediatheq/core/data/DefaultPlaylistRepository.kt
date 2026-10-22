package io.github.kentprodan.mediatheq.core.data

import androidx.sqlite.SQLiteException
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.map
import io.github.kentprodan.mediatheq.core.database.dao.PlaylistDao
import io.github.kentprodan.mediatheq.core.database.dao.TrackWithArtists
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryEntity
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import io.github.kentprodan.mediatheq.core.server.PlaylistEditor
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** A snapshot of one playlist as stored: its row and all its entries by position. */
private typealias Stored = Pair<PlaylistEntity, List<PlaylistEntryEntity>>

/**
 * Optimistic playlist edits (architecture skill, section 4). Edits run one at a time: each takes a snapshot of the
 * playlist, writes the expected result, calls the server, then reads the playlist back, or writes the snapshot back if
 * the server said no.
 */
@Singleton
class DefaultPlaylistRepository
    @Inject
    constructor(
        private val playlistDao: PlaylistDao,
        private val librarySource: LibrarySource,
        private val editor: PlaylistEditor,
        private val accountStore: AccountStore,
        private val sessionRepository: SessionRepository,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : PlaylistRepository {
        private val lock = Mutex()

        override fun playlist(id: MediaId): Flow<Playlist?> =
            playlistDao.playlist(id.serverId.value, id.itemId).map { it?.toDomain() }

        override fun items(id: MediaId): Flow<List<PlaylistItem>> =
            playlistDao.tracks(id.serverId.value, id.itemId).map { rows ->
                rows.map { PlaylistItem(it.entryId, it.position, TrackWithArtists(it.track, it.credits).toDomain()) }
            }

        override suspend fun create(name: String): MqResult<MediaId> =
            inAppScope {
                // Not optimistic: only the server can give the id.
                val created = withServer { editor.create(it, name) }
                if (created is MqResult.Success) {
                    val id = created.value
                    val stored =
                        storageSafe {
                            playlistDao.replace(PlaylistEntity(id.serverId.value, id.itemId, name, name), emptyList())
                            MqResult.Success(Unit)
                        }
                    if (stored is MqResult.Failure) return@inAppScope stored
                }
                created
            }

        override suspend fun rename(
            id: MediaId,
            name: String,
        ) = edit(id, { (row, entries) -> row.copy(name = name, sortName = name) to entries }) {
            editor.rename(it, id, name)
        }

        override suspend fun delete(id: MediaId): MqResult<Unit> =
            inAppScope {
                lock.withLock {
                    storageSafe {
                        // R5: the server deletes whatever item it is given, so only a stored playlist gets that far.
                        val snapshot = snapshot(id) ?: return@storageSafe MqResult.Failure(MqError.NotFound)
                        playlistDao.delete(id.serverId.value, id.itemId)
                        val result = withServer { editor.deletePlaylist(it, id) }
                        when {
                            result is MqResult.Success -> result

                            // Already deleted elsewhere: the goal is reached.
                            (result as MqResult.Failure).error == MqError.NotFound -> MqResult.Success(Unit)

                            else -> result.also { restore(snapshot) }
                        }
                    }
                }
            }

        override suspend fun add(
            id: MediaId,
            trackIds: List<MediaId>,
        ): MqResult<Unit> {
            if (trackIds.isEmpty()) return MqResult.Success(Unit)
            val result =
                edit(id, { (row, entries) ->
                    row to entries + trackIds.map { pendingEntry(row, it) }
                }) { editor.add(it, id, trackIds) }
            // R6: the server draws the cover collage about a second after tracks arrive; read it again then.
            // It is also a second chance for pending entries when the first read-back failed.
            scope.launch {
                delay(COLLAGE_DELAY)
                refresh(id)
            }
            return result
        }

        override suspend fun remove(
            id: MediaId,
            entryIds: Set<String>,
        ) = edit(id, { (row, entries) -> row to entries.filterNot { it.entryId in entryIds } }) {
            editor.remove(it, id, entryIds.toList())
        }

        override suspend fun move(
            id: MediaId,
            item: PlaylistItem,
            target: PlaylistItem,
        ): MqResult<Unit> {
            // Positions may have gaps (entries a sync dropped); the server counts the stored order.
            var to = -1
            return edit(id, { (row, entries) ->
                val from = entries.indexOfFirst { it.position == item.position && it.entryId == item.entryId }
                to = entries.indexOfFirst { it.position == target.position && it.entryId == target.entryId }
                // The screen's items may be a step behind the database; never move what is no longer there.
                if (from < 0 || to < 0) return@edit null
                row to entries.toMutableList().apply { add(to, removeAt(from)) }
            }) { editor.move(it, id, item.entryId, to) }
        }

        override suspend fun refresh(id: MediaId): MqResult<Unit> =
            inAppScope { lock.withLock { storageSafe { readBack(id) } } }

        /**
         * Writes what [change] makes of the stored playlist (entries renumbered), then runs [call]; a `null` change
         * means the edit no longer applies (`NotFound`). Then reads the playlist back, after a failure too: a chunked
         * request may have half succeeded. The snapshot stays when the server cannot be read.
         * Runs in the app scope: leaving the screen must not stop an edit between Room and the server.
         * shortcut: a library sync that staged this playlist before the edit swaps the old entries back in; the next
         * sync or read-back fixes it. Share one lock with the sync if that shows up in practice.
         */
        private suspend fun edit(
            id: MediaId,
            change: (Stored) -> Stored?,
            call: suspend (SignedInServer) -> MqResult<Unit>,
        ): MqResult<Unit> =
            inAppScope {
                lock.withLock {
                    storageSafe {
                        val snapshot = snapshot(id) ?: return@storageSafe MqResult.Failure(MqError.NotFound)
                        val (row, entries) = change(snapshot) ?: return@storageSafe MqResult.Failure(MqError.NotFound)
                        playlistDao.replaceAndCount(row, entries.mapIndexed { i, entry -> entry.copy(position = i) })
                        val result = withServer(call)
                        if (result is MqResult.Failure) restore(snapshot)
                        if ((result as? MqResult.Failure)?.error != MqError.Unauthorized) readBack(id)
                        result
                    }
                }
            }

        private suspend fun <T> inAppScope(block: suspend () -> MqResult<T>): MqResult<T> =
            scope.async { block() }.await()

        private suspend fun snapshot(id: MediaId): Stored? {
            val row = playlistDao.playlistRow(id.serverId.value, id.itemId) ?: return null
            return row to playlistDao.entryRows(id.serverId.value, id.itemId)
        }

        private suspend fun restore(snapshot: Stored) =
            withContext(NonCancellable) { playlistDao.replace(snapshot.first, snapshot.second) }

        /** Reads the playlist back from the server. A failure keeps what is stored and is returned. */
        private suspend fun readBack(id: MediaId): MqResult<Unit> {
            val server = accountStore.signedInServer() ?: return MqResult.Failure(MqError.Unauthorized)
            val playlist =
                when (val result = librarySource.playlist(server, id)) {
                    is MqResult.Success -> {
                        result.value
                    }

                    is MqResult.Failure -> {
                        if (result.error == MqError.NotFound) playlistDao.delete(id.serverId.value, id.itemId)
                        return result.map {}
                    }
                }
            val entries = mutableListOf<PlaylistEntry>()
            // Paged by server offset: the mapper may drop an entry, so the count read is not the next start.
            var start = 0
            do {
                val page = librarySource.playlistEntries(server, id, start, PAGE_SIZE)
                if (page !is MqResult.Success) return page.map {}
                entries += page.value.items
                start += PAGE_SIZE
            } while (start < page.value.totalCount)
            playlistDao.replace(playlist.toEntity(), entries.mapIndexed { i, entry -> entry.toEntity(id, i) })
            return MqResult.Success(Unit)
        }

        private suspend fun <T> withServer(call: suspend (SignedInServer) -> MqResult<T>): MqResult<T> {
            // Signed out meanwhile: nothing to do, and not a session expiry.
            val server = accountStore.signedInServer() ?: return MqResult.Failure(MqError.Unauthorized)
            val result = call(server)
            if ((result as? MqResult.Failure)?.error == MqError.Unauthorized) {
                withContext(NonCancellable) { sessionRepository.onUnauthorized() }
            }
            return result
        }

        private fun pendingEntry(
            row: PlaylistEntity,
            trackId: MediaId,
        ) = PlaylistEntryEntity(row.serverId, row.id, 0, PlaylistItem.PENDING_ENTRY_ID, trackId.itemId)

        private companion object {
            val COLLAGE_DELAY = 2.seconds
            const val PAGE_SIZE = 500
        }
    }

/** A database failure during an edit becomes [MqError.Storage]. */
private inline fun <T> storageSafe(block: () -> MqResult<T>): MqResult<T> =
    try {
        block()
    } catch (_: SQLiteException) {
        MqResult.Failure(MqError.Storage)
    }
