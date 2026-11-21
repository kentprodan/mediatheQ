package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.map
import io.github.kentprodan.mediatheq.core.database.dao.PlaylistDao
import io.github.kentprodan.mediatheq.core.database.dao.PlaylistTrack
import io.github.kentprodan.mediatheq.core.database.dao.TrackWithArtists
import io.github.kentprodan.mediatheq.core.database.entity.PendingPlaylistEditEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryEntity
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.PlaylistChange
import io.github.kentprodan.mediatheq.core.model.PlaylistConflict
import io.github.kentprodan.mediatheq.core.model.PlaylistEntry
import io.github.kentprodan.mediatheq.core.model.PlaylistResolution
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import io.github.kentprodan.mediatheq.core.server.PlaylistEditor
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
        private val filter: OfflineFilter,
        private val clock: Clock,
    ) : PlaylistRepository {
        private val lock = Mutex()

        override fun playlist(id: MediaId): Flow<Playlist?> =
            playlistDao.playlist(id.serverId.value, id.itemId).map { it?.toDomain() }

        override fun items(id: MediaId): Flow<List<PlaylistItem>> =
            filter
                .of {
                    all,
                    downloaded,
                    ->
                    playlistDao.tracks(id.serverId.value, id.itemId, all, downloaded)
                }.map(::toItems)

        override fun allItems(id: MediaId): Flow<List<PlaylistItem>> =
            playlistDao.tracks(id.serverId.value, id.itemId, all = true, downloaded = emptyList()).map(::toItems)

        private fun toItems(rows: List<PlaylistTrack>): List<PlaylistItem> =
            rows.map {
                PlaylistItem(
                    it.entryId,
                    it.position,
                    TrackWithArtists(it.track, it.credits, it.album).toDomain(),
                )
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
        ) = edit(
            id,
            { (row, entries) -> row.copy(name = name, sortName = name) to entries },
            Pending(EDIT_RENAME, name = name),
        ) {
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
                }, Pending(EDIT_ADD, trackIds = trackIds.map { it.itemId })) { editor.add(it, id, trackIds) }
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
        ) = edit(
            id,
            { (row, entries) -> row to entries.filterNot { it.entryId in entryIds } },
            Pending(EDIT_REMOVE, entryIds = entryIds.toList()),
        ) {
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
            }, {
                Pending(
                    EDIT_MOVE,
                    entryIds = listOf(item.entryId),
                    position = to,
                )
            }) { editor.move(it, id, item.entryId, to) }
        }

        override suspend fun refresh(id: MediaId): MqResult<Unit> =
            inAppScope {
                lock.withLock {
                    storageSafe {
                        // While edits wait (#417) the phone's version stays on screen; the replay reads the server.
                        val server = accountStore.signedInServer()
                        if (server != null && waiting(server, id)) MqResult.Success(Unit) else readBack(id)
                    }
                }
            }

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
            pending: Pending,
            call: suspend (SignedInServer) -> MqResult<Unit>,
        ): MqResult<Unit> = edit(id, change, { pending }, call)

        /**
         * #417: while edits of this playlist wait, or when the server cannot be reached, the change stays here and
         * [pending] (read after [change], which may compute a position) joins the queue, sent in order when the server
         * is back ([flushPending]).
         */
        private suspend fun edit(
            id: MediaId,
            change: (Stored) -> Stored?,
            pending: () -> Pending,
            call: suspend (SignedInServer) -> MqResult<Unit>,
        ): MqResult<Unit> =
            inAppScope {
                lock.withLock {
                    storageSafe {
                        val snapshot = snapshot(id) ?: return@storageSafe MqResult.Failure(MqError.NotFound)
                        val (row, entries) = change(snapshot) ?: return@storageSafe MqResult.Failure(MqError.NotFound)
                        playlistDao.replaceAndCount(row, entries.mapIndexed { i, entry -> entry.copy(position = i) })
                        val server = accountStore.signedInServer()
                        if (server != null && waiting(server, id)) {
                            return@storageSafe queue(server, id, pending(), snapshot.version())
                        }
                        val result = withServer(call)
                        if (server != null && (result as? MqResult.Failure)?.error == MqError.Network) {
                            // shortcut: a chunked add cut off half way counts its sent part as a server change at
                            // the replay (a conflict to answer); track the sent chunks if that shows up.
                            return@storageSafe queue(server, id, pending(), snapshot.version())
                        }
                        if (result is MqResult.Failure) restore(snapshot)
                        if ((result as? MqResult.Failure)?.error != MqError.Unauthorized) readBack(id)
                        result
                    }
                }
            }

        private suspend fun waiting(
            server: SignedInServer,
            id: MediaId,
        ) = playlistDao.pendingEditCount(server.serverId.value, server.userId, id.itemId) > 0

        /** Queues [pending]; the first edit of a playlist records [base], the version the server had, for the rest. */
        private suspend fun queue(
            server: SignedInServer,
            id: MediaId,
            pending: Pending,
            base: PlaylistVersion,
        ): MqResult<Unit> {
            val earlier = pendingEdits(server, id.itemId).firstOrNull()
            playlistDao.insertPendingEdit(
                PendingPlaylistEditEntity(
                    serverId = server.serverId.value,
                    userId = server.userId,
                    playlistId = id.itemId,
                    kind = pending.kind,
                    trackIds = pending.trackIds?.joinToString(ID_SEPARATOR),
                    entryIds = pending.entryIds?.joinToString(ID_SEPARATOR),
                    position = pending.position,
                    name = pending.name,
                    baseName = if (earlier != null) earlier.baseName else base.name,
                    baseTrackIds =
                        if (earlier != null) earlier.baseTrackIds else base.trackIds.joinToString(ID_SEPARATOR),
                    queuedAt = clock.now().toEpochMilliseconds(),
                ),
            )
            return MqResult.Success(Unit)
        }

        private suspend fun pendingEdits(
            server: SignedInServer,
            playlistId: String,
        ) = playlistDao.pendingEdits(server.serverId.value, server.userId).filter { it.playlistId == playlistId }

        private val conflictState = MutableStateFlow<List<PlaylistConflict>>(emptyList())
        override val conflicts: StateFlow<List<PlaylistConflict>> = conflictState.asStateFlow()

        private val droppedState = MutableStateFlow<List<DroppedPlaylistEdits>>(emptyList())
        override val droppedEdits: StateFlow<List<DroppedPlaylistEdits>> = droppedState.asStateFlow()

        override fun acknowledgeDroppedEdits() {
            droppedState.value = emptyList()
        }

        /**
         * Sends the playlist edits made offline (#417), per playlist in order: the playlist is read from the server
         * first. When the server changed it since the edits' base, the phone's version stays on screen and the
         * playlist waits in [conflicts] for the owner's answer; otherwise each edit is replayed ([sendAll]). A
         * playlist deleted or forbidden on the server drops its edits with a [droppedEdits] message; no network keeps
         * the rest for next time.
         */
        internal suspend fun flushPending() {
            val server = accountStore.signedInServer() ?: return
            lock.withLock {
                storageSafe {
                    val found = mutableListOf<PlaylistConflict>()
                    // Each connection asks again, also about a conflict the owner put off with Later.
                    conflictState.value = emptyList()
                    try {
                        val edits = playlistDao.pendingEdits(server.serverId.value, server.userId)
                        edits.groupBy { it.playlistId }.forEach { (playlistId, list) ->
                            val id = MediaId(server.serverId, playlistId)
                            val phone = snapshot(id)
                            if (!readServer(server, id, list, phone)) {
                                return@storageSafe MqResult.Failure(MqError.Network)
                            }
                            if (!waiting(server, id)) return@forEach
                            val conflict = conflict(id, list)
                            if (conflict == null) {
                                if (sendAll(server, id, list) is MqResult.Failure) {
                                    // The rest waits; the phone's version stays on screen until it goes.
                                    phone?.let { restore(it) }
                                    return@storageSafe MqResult.Failure(MqError.Network)
                                }
                            } else {
                                // The server's version stays out of sight until the owner answers.
                                phone?.let { restore(it) }
                                found += conflict
                            }
                        }
                    } finally {
                        conflictState.value = found
                    }
                    MqResult.Success(Unit)
                }
            }
        }

        /**
         * Reads the playlist from the server before its edits go. Deleted or forbidden there: its edits are dropped
         * with a message. `false` when it could not be read for another reason (no network, a server error): the edits
         * wait.
         */
        private suspend fun readServer(
            server: SignedInServer,
            id: MediaId,
            edits: List<PendingPlaylistEditEntity>,
            stored: Stored?,
        ): Boolean {
            val read = readBack(id) as? MqResult.Failure ?: return true
            if (!read.error.dropsEdits()) return false
            playlistDao.deletePendingEdits(server.serverId.value, server.userId, id.itemId)
            dropped(stored, edits, read.error)
            return true
        }

        private fun dropped(
            stored: Stored?,
            edits: List<PendingPlaylistEditEntity>,
            error: MqError,
        ) {
            if (!error.dropsEdits()) return
            val name = stored?.first?.name ?: edits.firstNotNullOfOrNull { it.baseName } ?: return
            droppedState.update { it + DroppedPlaylistEdits(name, error) }
        }

        /**
         * The conflict when the server's version (as just read) differs from the edits' base; edits queued before
         * schema 20 have no base and are merged.
         */
        private suspend fun conflict(
            id: MediaId,
            edits: List<PendingPlaylistEditEntity>,
        ): PlaylistConflict? {
            val base = edits.first().base() ?: return null
            val server = snapshot(id)?.version() ?: return null
            val serverChange = base.changeTo(server) ?: return null
            val phone = base.applyAll(edits)
            return PlaylistConflict(
                playlistId = id,
                name = phone.name,
                since = edits.first().queuedAt?.let(Instant::fromEpochMilliseconds),
                server = serverChange,
                phone = base.changeTo(phone) ?: PlaylistChange(),
            )
        }

        override suspend fun resolve(
            id: MediaId,
            resolution: PlaylistResolution,
        ): MqResult<Unit> =
            inAppScope {
                lock.withLock {
                    storageSafe {
                        val server =
                            accountStore.signedInServer() ?: return@storageSafe MqResult.Failure(MqError.Unauthorized)
                        val edits = pendingEdits(server, id.itemId)
                        val phone = snapshot(id)
                        val result =
                            if (edits.isEmpty() || resolution == PlaylistResolution.Skip) {
                                MqResult.Success(Unit)
                            } else {
                                resolveWith(server, id, edits, resolution)
                            }
                        val error = (result as? MqResult.Failure)?.error
                        if (error != null && !error.dropsEdits()) {
                            // Asked again; the phone's version stays on screen meanwhile.
                            phone?.let { restore(it) }
                            return@storageSafe result
                        }
                        playlistDao.deletePendingEdits(server.serverId.value, server.userId, id.itemId)
                        conflictState.update { all -> all.filterNot { it.playlistId == id } }
                        readBack(id)
                        result
                    }
                }
            }

        private suspend fun resolveWith(
            server: SignedInServer,
            id: MediaId,
            edits: List<PendingPlaylistEditEntity>,
            resolution: PlaylistResolution,
        ): MqResult<Unit> {
            if (!readServer(server, id, edits, snapshot(id))) return MqResult.Failure(MqError.Network)
            // Dropped by the read (deleted or forbidden on the server).
            if (!waiting(server, id)) return MqResult.Success(Unit)
            return if (resolution ==
                PlaylistResolution.Replace
            ) {
                replace(server, id, edits)
            } else {
                sendAll(server, id, edits)
            }
        }

        /**
         * The phone's version wins: every server entry goes, then the phone's tracks in order and its name. Computed
         * from the base and the edits, never from the server, so running it again after a half done one is right.
         */
        private suspend fun replace(
            server: SignedInServer,
            id: MediaId,
            edits: List<PendingPlaylistEditEntity>,
        ): MqResult<Unit> {
            val base = edits.first().base() ?: return sendAll(server, id, edits)
            val phone = base.applyAll(edits)
            val serverNow = snapshot(id)
            val entries =
                serverNow
                    ?.second
                    ?.map { it.entryId }
                    ?.distinct()
                    .orEmpty()
            val tracks = phone.trackIds.map { MediaId(server.serverId, it) }
            if (entries.isNotEmpty()) {
                withServer { editor.remove(it, id, entries) }.let { if (it is MqResult.Failure) return it }
            }
            if (tracks.isNotEmpty()) {
                withServer { editor.add(it, id, tracks) }.let { if (it is MqResult.Failure) return it }
            }
            if (serverNow?.first?.name == phone.name) return MqResult.Success(Unit)
            return withServer { editor.rename(it, id, phone.name) }
        }

        /**
         * Replays [edits] in order; one that no longer applies (its entry is gone) is dropped quietly. After each, the
         * base moves to what the server then has, so a later replay never takes our own sent edits for a server change.
         */
        private suspend fun sendAll(
            server: SignedInServer,
            id: MediaId,
            edits: List<PendingPlaylistEditEntity>,
        ): MqResult<Unit> {
            for (edit in edits) {
                val stored = snapshot(id)
                val entries = stored?.second?.map { it.entryId }.orEmpty()
                val result = replay(server, id, edit, entries)
                val error = (result as? MqResult.Failure)?.error
                if (error != null && !error.dropsEdits()) return result
                if (error != null) {
                    playlistDao.deletePendingEdits(server.serverId.value, server.userId, id.itemId)
                    dropped(stored, edits, error)
                    return MqResult.Success(Unit)
                }
                playlistDao.deletePendingEdit(edit.seq)
                if (readBack(id) is MqResult.Success) {
                    snapshot(id)?.version()?.let { now ->
                        playlistDao.updatePendingBase(
                            server.serverId.value,
                            server.userId,
                            id.itemId,
                            now.name,
                            now.trackIds.joinToString(ID_SEPARATOR),
                        )
                    }
                }
            }
            return MqResult.Success(Unit)
        }

        private suspend fun replay(
            server: SignedInServer,
            id: MediaId,
            edit: PendingPlaylistEditEntity,
            /** Every entry id the server has, in order (R13: a track twice is there twice). */
            serverEntries: List<String>,
        ): MqResult<Unit> {
            val entryIds = edit.entryIds.ids().filter { it in serverEntries }
            return when (edit.kind) {
                EDIT_ADD -> {
                    val tracks = edit.trackIds.ids().map { MediaId(server.serverId, it) }
                    if (tracks.isEmpty()) MqResult.Success(Unit) else withServer { editor.add(it, id, tracks) }
                }

                EDIT_REMOVE -> {
                    if (entryIds.isEmpty()) MqResult.Success(Unit) else withServer { editor.remove(it, id, entryIds) }
                }

                EDIT_MOVE -> {
                    val entry = entryIds.firstOrNull()
                    val to = (edit.position ?: 0).coerceIn(0, (serverEntries.size - 1).coerceAtLeast(0))
                    if (entry == null) MqResult.Success(Unit) else withServer { editor.move(it, id, entry, to) }
                }

                EDIT_RENAME -> {
                    edit.name?.let { name -> withServer { editor.rename(it, id, name) } } ?: MqResult.Success(Unit)
                }

                else -> {
                    MqResult.Success(Unit)
                }
            }
        }

        /** An edit as it waits for the server (#417). */
        private data class Pending(
            val kind: String,
            val trackIds: List<String>? = null,
            val entryIds: List<String>? = null,
            val position: Int? = null,
            val name: String? = null,
        )

        private suspend fun <T> inAppScope(block: suspend () -> MqResult<T>): MqResult<T> =
            scope.async { block() }.await()

        private suspend fun snapshot(id: MediaId): Stored? {
            val row = playlistDao.playlistRow(id.serverId.value, id.itemId) ?: return null
            return row to playlistDao.entryRows(id.serverId.value, id.itemId)
        }

        private fun Stored.version() = PlaylistVersion(first.name, second.map { it.trackId })

        /** Only a playlist deleted on the server or one the user may no longer edit drops waiting edits. */
        private fun MqError.dropsEdits() = this == MqError.NotFound || this == MqError.Forbidden

        private fun PendingPlaylistEditEntity.base(): PlaylistVersion? =
            baseName?.let { PlaylistVersion(it, baseTrackIds.ids()) }

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
