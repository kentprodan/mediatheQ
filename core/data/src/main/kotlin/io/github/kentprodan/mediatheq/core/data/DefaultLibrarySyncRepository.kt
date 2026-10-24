package io.github.kentprodan.mediatheq.core.data

import androidx.sqlite.SQLiteException
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.database.dao.SyncDao
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistStaging
import io.github.kentprodan.mediatheq.core.database.entity.SyncStateEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.server.LibraryPage
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Full library sync with resume (issue #9). Pages land in the staging tables together with a checkpoint; when every
 * step is done, one transaction swaps them in. [start] ties the library to the signed-in account: it syncs when a sync
 * is due and wipes everything on sign-out, so a sync in flight can never bring a signed-out library back.
 */
@Singleton
class DefaultLibrarySyncRepository
    @Inject
    constructor(
        private val librarySource: LibrarySource,
        private val accountStore: AccountStore,
        private val syncDao: SyncDao,
        private val sessionRepository: SessionRepository,
        private val clock: Clock,
        @param:ApplicationScope private val scope: CoroutineScope,
        private val userData: DefaultUserDataRepository,
    ) : LibrarySyncRepository {
        private val mutableStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle(null))
        override val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

        private val lock = Mutex()
        private val restartedSteps = mutableSetOf<SyncStep>()
        private var running: Deferred<MqResult<Unit>>? = null

        override suspend fun sync(): MqResult<Unit> = join().await()

        /** Follows the account for the app's lifetime; call once from the Application. */
        fun start() {
            scope.launch {
                accountStore.account
                    .map {
                        (it as? MqResult.Success)?.value?.let { account ->
                            account.serverId.value to account.userId
                        }
                    }.distinctUntilChanged()
                    .collectLatest { signedIn ->
                        try {
                            if (signedIn == null) {
                                wipe()
                            } else {
                                // Another account's library must never show, even if a wipe was interrupted.
                                val owner = syncDao.state()?.let { it.ownerServerId to it.ownerUserId }
                                if (owner != null && owner.first != null && owner != signedIn) wipe()
                                mutableStatus.value = SyncStatus.Idle(lastSyncedAt())
                                if (isDue(signedIn)) sync()
                            }
                        } catch (_: SQLiteException) {
                            mutableStatus.value = SyncStatus.Failed(MqError.Storage, null)
                        }
                    }
            }
        }

        private suspend fun join(): Deferred<MqResult<Unit>> =
            lock.withLock {
                running?.takeIf { it.isActive } ?: scope.async { runSync() }.also { running = it }
            }

        /** Cancels any sync, then forgets the library. Not cancellable itself: a half-done wipe would leak data. */
        private suspend fun wipe() =
            withContext(NonCancellable) {
                val job = lock.withLock { running }
                job?.cancel()
                job?.join()
                syncDao.clearAll()
                mutableStatus.value = SyncStatus.Idle(null)
            }

        /** Due when never synced, when an interrupted sync can resume, or when the last one is over a day old. */
        internal suspend fun isDue(signedIn: Pair<String, String>): Boolean {
            val state = syncDao.state() ?: return true
            val last = state.lastFullSyncAtMillis ?: return true
            if (state.checkpointStep != null && state.belongsTo(signedIn)) return true
            return clock.now() - Instant.fromEpochMilliseconds(last) > SYNC_INTERVAL
        }

        private suspend fun lastSyncedAt(): Instant? =
            syncDao.state()?.lastFullSyncAtMillis?.let(Instant::fromEpochMilliseconds)

        private suspend fun runSync(): MqResult<Unit> {
            // Signed out meanwhile: nothing to sync, and not a session expiry.
            val account =
                (accountStore.account.first() as? MqResult.Success)?.value
                    ?: return MqResult.Failure(MqError.Unauthorized)
            val result =
                try {
                    fetchAll(signedIn(account))
                } catch (_: SQLiteException) {
                    MqResult.Failure(MqError.Storage)
                }
            mutableStatus.value =
                try {
                    when (result) {
                        is MqResult.Success -> SyncStatus.Idle(lastSyncedAt())
                        is MqResult.Failure -> SyncStatus.Failed(result.error, lastSyncedAt())
                    }
                } catch (_: SQLiteException) {
                    SyncStatus.Failed(MqError.Storage, null)
                }
            if ((result as? MqResult.Failure)?.error == MqError.Unauthorized) {
                // Signing out wipes, which cancels this sync; the expiry notice must still be recorded.
                withContext(NonCancellable) { sessionRepository.onUnauthorized() }
            }
            return result
        }

        private suspend fun signedIn(account: StoredAccount) = accountStore.signedInServer(account)

        private suspend fun fetchAll(server: SignedInServer): MqResult<Unit> {
            val account = server.serverId.value to server.userId
            var state = syncDao.state() ?: SyncStateEntity()
            val now = clock.now().toEpochMilliseconds()
            restartedSteps.clear()
            val resumable =
                SyncStep.entries.any { it.name == state.checkpointStep } &&
                    state.belongsTo(account) &&
                    now - (state.checkpointStartedAtMillis ?: 0) < RESUME_WINDOW.inWholeMilliseconds
            if (!resumable) {
                // Offsets drift when the library changes, so an old or foreign checkpoint is not worth resuming.
                syncDao.discardStaging()
                state =
                    SyncStateEntity(
                        lastFullSyncAtMillis = state.lastFullSyncAtMillis,
                        checkpointServerId = account.first,
                        checkpointUserId = account.second,
                        checkpointStartedAtMillis = now,
                        checkpointStep = SyncStep.entries.first().name,
                    )
                syncDao.saveState(state)
            }
            var step = SyncStep.entries.first { it.name == state.checkpointStep }
            var index = state.checkpointIndex
            while (true) {
                when (val next = fetchPage(server, state, step, index)) {
                    is MqResult.Failure -> {
                        return next
                    }

                    is MqResult.Success -> {
                        val (nextStep, nextIndex) = next.value ?: break
                        step = nextStep
                        index = nextIndex
                    }
                }
            }
            syncDao.swap(clock.now().toEpochMilliseconds(), account.first, account.second)
            // Pages fetched before a favorite or rating reached the server carry the old value.
            userData.reapplyOverrides()
            return MqResult.Success(Unit)
        }

        /** Fetches one page of [step] at [index] and stages it; returns where to continue, `null` when done. */
        private suspend fun fetchPage(
            server: SignedInServer,
            state: SyncStateEntity,
            step: SyncStep,
            index: Int,
        ): MqResult<Pair<SyncStep, Int>?> =
            when (step) {
                SyncStep.AlbumArtists -> {
                    stagePage(state, step, index, librarySource.albumArtists(server, index, PAGE_SIZE)) { artists ->
                        stageAlbumArtists(artists.map { ArtistStaging(it.toEntity(isAlbumArtist = true)) })
                    }
                }

                SyncStep.Albums -> {
                    stagePage(state, step, index, librarySource.albums(server, index, PAGE_SIZE)) { albums ->
                        stageAlbums(albums.map { AlbumStaging(it.toEntity()) })
                        stageAlbumArtistLinks(albums.flatMap { it.artistLinks() }.map(::AlbumArtistStaging))
                        stageCreditedArtists(
                            albums.flatMap { it.albumArtists }.map { ArtistStaging(it.toEntity(false)) },
                        )
                    }
                }

                SyncStep.Tracks -> {
                    stagePage(state, step, index, librarySource.tracks(server, index, PAGE_SIZE)) { tracks ->
                        stageTracks(tracks.map { TrackStaging(it.toEntity()) })
                        stageTrackArtistLinks(tracks.flatMap { it.artistLinks() }.map(::TrackArtistStaging))
                        stageCreditedArtists(tracks.flatMap { it.artists }.map { ArtistStaging(it.toEntity(false)) })
                    }
                }

                SyncStep.Playlists -> {
                    stagePage(state, step, index, librarySource.playlists(server, index, PAGE_SIZE)) { playlists ->
                        stagePlaylists(playlists.map { PlaylistStaging(it.toEntity()) })
                    }
                }

                SyncStep.PlaylistEntries -> {
                    fetchPlaylistEntries(server, state, index)
                }
            }

        /** Stages a fetched [page] with the checkpoint of the position after it. */
        private suspend fun <T> stagePage(
            state: SyncStateEntity,
            step: SyncStep,
            index: Int,
            page: MqResult<LibraryPage<T>>,
            write: suspend SyncDao.(List<T>) -> Unit,
        ): MqResult<Pair<SyncStep, Int>?> {
            val value =
                when (page) {
                    is MqResult.Failure -> return page
                    is MqResult.Success -> page.value
                }
            val nextIndex = index + PAGE_SIZE
            var next = if (nextIndex < value.totalCount && value.items.isNotEmpty()) step to nextIndex else step.next()
            syncDao.stagePage(state.at(next)) { write(value.items) }
            if (next.first != step && stagedCount(step) < value.totalCount && restartedSteps.add(step)) {
                // Offsets shifted (an item added or removed mid-sync) and one slipped between pages: fetch it again.
                next = step to 0
                syncDao.stagePage(state.at(next)) { clearStaging(step) }
            }
            mutableStatus.value = SyncStatus.Running(step, minOf(nextIndex, value.totalCount), value.totalCount)
            return MqResult.Success(next)
        }

        private suspend fun stagedCount(step: SyncStep): Int =
            when (step) {
                SyncStep.AlbumArtists -> syncDao.stagedAlbumArtistCount()
                SyncStep.Albums -> syncDao.stagedAlbumCount()
                SyncStep.Tracks -> syncDao.stagedTrackCount()
                SyncStep.Playlists -> syncDao.stagedPlaylistCount()
                SyncStep.PlaylistEntries -> Int.MAX_VALUE
            }

        private suspend fun SyncDao.clearStaging(step: SyncStep) {
            when (step) {
                SyncStep.AlbumArtists -> {
                    clearArtistStaging()
                }

                SyncStep.Albums -> {
                    clearAlbumStaging()
                    clearAlbumArtistStaging()
                }

                SyncStep.Tracks -> {
                    clearTrackStaging()
                    clearTrackArtistStaging()
                }

                SyncStep.Playlists -> {
                    clearPlaylistStaging()
                }

                SyncStep.PlaylistEntries -> {
                    clearPlaylistEntryStaging()
                }
            }
        }

        /** Fetches every entry of the playlist at [playlistIndex] and stages them as one checkpointed unit. */
        private suspend fun fetchPlaylistEntries(
            server: SignedInServer,
            state: SyncStateEntity,
            playlistIndex: Int,
        ): MqResult<Pair<SyncStep, Int>?> {
            val playlistIds = syncDao.stagedPlaylistIds()
            val playlistId = playlistIds.getOrNull(playlistIndex)?.let { MediaId(server.serverId, it) }
            // Done: the checkpoint stays until the swap, so a failed swap resumes instead of fetching everything again.
            if (playlistId == null) return MqResult.Success(null)
            val entries = mutableListOf<PlaylistEntryStaging>()
            var start = 0
            do {
                val page = librarySource.playlistEntries(server, playlistId, start, PAGE_SIZE)
                if (page is MqResult.Failure) {
                    // Deleted or hidden since the playlist list was read: keep it without entries, never block the sync.
                    if (page.error != MqError.NotFound && page.error != MqError.Forbidden) return page
                    entries.clear()
                    break
                }
                val value = (page as MqResult.Success).value
                value.items.forEachIndexed { i, entry ->
                    entries +=
                        PlaylistEntryStaging(entry.toEntity(playlistId, start + i))
                }
                start += PAGE_SIZE
            } while (start < value.totalCount && value.items.isNotEmpty())
            val next = SyncStep.PlaylistEntries to playlistIndex + 1
            syncDao.stagePage(state.at(next)) { stagePlaylistEntries(entries) }
            mutableStatus.value = SyncStatus.Running(SyncStep.PlaylistEntries, playlistIndex + 1, playlistIds.size)
            return MqResult.Success(next)
        }

        private fun SyncStateEntity.at(next: Pair<SyncStep, Int>?) =
            copy(
                checkpointStep = next?.first?.name,
                checkpointIndex =
                    next?.second ?: 0,
            )

        private fun SyncStateEntity.belongsTo(account: Pair<String, String>) =
            checkpointServerId == account.first && checkpointUserId == account.second

        private fun SyncStep.next(): Pair<SyncStep, Int> = SyncStep.entries[ordinal + 1] to 0

        private companion object {
            const val PAGE_SIZE = 500
            val SYNC_INTERVAL = 24.hours
            val RESUME_WINDOW = 1.hours
        }
    }
