package io.github.kentprodan.mediatheq.core.data

import androidx.sqlite.SQLiteException
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
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

        override suspend fun create(
            name: String,
            trackIds: List<MediaId>,
        ): MqResult<MediaId> {
            // Not optimistic: only the server can give the id. Tracks then follow as an ordinary (optimistic) add.
            val created = withServer { editor.create(it, name) }
            if (created !is MqResult.Success) return created
            val id = created.value
            val stored =
                storageSafe {
                    playlistDao.replace(PlaylistEntity(id.serverId.value, id.itemId, name, name), emptyList())
                    MqResult.Success(Unit)
                }
            if (stored is MqResult.Failure) return stored
            return when (val added = add(id, trackIds)) {
                is MqResult.Success -> created
                is MqResult.Failure -> added
            }
        }

        override suspend fun rename(
            id: MediaId,
            name: String,
        ) = edit(id, { (row, entries) -> row.copy(name = name, sortName = name) to entries }) {
            editor.rename(it, id, name)
        }

        override suspend fun delete(id: MediaId): MqResult<Unit> =
            lock.withLock {
                storageSafe {
                    // R5: the server deletes whatever item it is given, so only a stored playlist gets that far.
                    val snapshot = snapshot(id) ?: return@storageSafe MqResult.Failure(MqError.NotFound)
                    playlistDao.delete(id.serverId.value, id.itemId)
                    withServer { editor.deletePlaylist(it, id) }.also { if (it is MqResult.Failure) restore(snapshot) }
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
            if (result is MqResult.Success) {
                // R6: the server draws the cover collage about a second after tracks arrive; read it again then.
                scope.launch {
                    delay(COLLAGE_DELAY)
                    lock.withLock { storageSafe { refresh(id) } }
                }
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
        ) = edit(id, { (row, entries) ->
            // The screen's items may be a step behind the database; never move what is no longer there.
            val current =
                entries.getOrNull(item.position)?.entryId == item.entryId && target.position in entries.indices
            if (!current) return@edit null
            row to entries.toMutableList().apply { add(target.position, removeAt(item.position)) }
        }) { editor.move(it, id, item.entryId, target.position) }

        /**
         * Writes what [change] makes of the stored playlist (entries renumbered), then runs [call]. A `null` change
         * means the edit no longer applies (`NotFound`).
         */
        private suspend fun edit(
            id: MediaId,
            change: (Stored) -> Stored?,
            call: suspend (SignedInServer) -> MqResult<Unit>,
        ): MqResult<Unit> =
            lock.withLock {
                storageSafe {
                    val snapshot = snapshot(id) ?: return@storageSafe MqResult.Failure(MqError.NotFound)
                    val (row, entries) = change(snapshot) ?: return@storageSafe MqResult.Failure(MqError.NotFound)
                    playlistDao.replaceAndCount(row, entries.mapIndexed { i, entry -> entry.copy(position = i) })
                    withServer(call).also {
                        when (it) {
                            is MqResult.Success -> refresh(id)
                            is MqResult.Failure -> restore(snapshot)
                        }
                    }
                }
            }

        private suspend fun snapshot(id: MediaId): Stored? {
            val row = playlistDao.playlistRow(id.serverId.value, id.itemId) ?: return null
            return row to playlistDao.entryRows(id.serverId.value, id.itemId)
        }

        private suspend fun restore(snapshot: Stored) =
            withContext(NonCancellable) { playlistDao.replace(snapshot.first, snapshot.second) }

        /** Reads the playlist back from the server. Best effort: a failure keeps what is stored. */
        private suspend fun refresh(id: MediaId): MqResult<Unit> {
            val server = accountStore.signedInServer() ?: return MqResult.Success(Unit)
            val playlist =
                when (val result = librarySource.playlist(server, id)) {
                    is MqResult.Success -> {
                        result.value
                    }

                    is MqResult.Failure -> {
                        if (result.error == MqError.NotFound) playlistDao.delete(id.serverId.value, id.itemId)
                        return MqResult.Success(Unit)
                    }
                }
            val entries = mutableListOf<PlaylistEntry>()
            do {
                val page = librarySource.playlistEntries(server, id, entries.size, PAGE_SIZE)
                if (page !is MqResult.Success) return MqResult.Success(Unit)
                entries += page.value.items
            } while (page.value.items.isNotEmpty() && entries.size < page.value.totalCount)
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
