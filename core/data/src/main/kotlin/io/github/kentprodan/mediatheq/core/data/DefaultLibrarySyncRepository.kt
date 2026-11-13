package io.github.kentprodan.mediatheq.core.data

import androidx.sqlite.SQLiteException
import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.map
import io.github.kentprodan.mediatheq.core.database.dao.SyncDao
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumGenreStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumLabelStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistStaging
import io.github.kentprodan.mediatheq.core.database.entity.SeasonStaging
import io.github.kentprodan.mediatheq.core.database.entity.SeriesStaging
import io.github.kentprodan.mediatheq.core.database.entity.SyncStateEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackGenreStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.database.entity.VideoStaging
import io.github.kentprodan.mediatheq.core.database.entity.VideoStreamStaging
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.datastore.StoredSyncRecord
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.server.LibraryPage
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import io.github.kentprodan.mediatheq.core.server.ServerCapabilities
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import java.io.IOException
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
        private val log: AppLog,
        private val capabilities: ServerCapabilities,
        private val appMode: AppModeRepository,
    ) : LibrarySyncRepository {
        private val mutableStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle(null))
        override val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

        private val lock = Mutex()
        private val restartedSteps = mutableSetOf<SyncStep>()
        private var running: Deferred<MqResult<Unit>>? = null

        /** The steps of the run in progress: the app mode's kinds (ADR 0051), read when it starts. */
        private var runSteps: List<SyncStep> = SyncStep.entries

        /** Music and video: whether the run in progress fetches them. */
        @Volatile private var runKinds: Pair<Boolean, Boolean>? = null

        override suspend fun sync(): MqResult<Unit> {
            val job = join()
            return try {
                job.await()
            } catch (e: CancellationException) {
                // The caller itself cancelled: pass it on. Otherwise signing out cancelled the sync: a plain failure, so
                // whoever waited (e.g. live sync's loop) keeps going (#51).
                currentCoroutineContext().ensureActive()
                MqResult.Failure(MqError.Unauthorized)
            }
        }

        /** Like [sync], but never joins a sync that was already running: it may have read past the change. */
        internal suspend fun syncAfterChange(): MqResult<Unit> {
            lock.withLock { running }?.join()
            return sync()
        }

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
            scope.launch {
                appMode.mode.collectLatest { mode ->
                    try {
                        syncNewKinds(mode)
                    } catch (_: SQLiteException) {
                        mutableStatus.value = SyncStatus.Failed(MqError.Storage, null)
                    }
                }
            }
        }

        /**
         * A kind [mode] turns on that was never synced on this account, or not for a day, is synced now (ADR 0051). A
         * running sync of a kind [mode] drops (Music, then Video picked right after sign-in) is stopped; one that only
         * misses a kind is waited for, since it may sync that kind meanwhile.
         */
        internal suspend fun syncNewKinds(mode: AppMode) {
            val kinds = mode.kinds()
            val job = lock.withLock { running?.takeIf { it.isActive } }
            val current = runKinds
            if (job != null && current != null &&
                ((current.first && !kinds.first) || (current.second && !kinds.second))
            ) {
                job.cancelAndJoin()
            } else {
                job?.join()
            }
            if (accountStore.account.first() !is MqResult.Success) return
            if (stale(kinds)) sync()
        }

        private fun AppMode.kinds() = music to (video && capabilities.video)

        /** Whether one of [kinds] (music, video) was never swapped in, or longer ago than [SYNC_INTERVAL]. */
        private suspend fun stale(kinds: Pair<Boolean, Boolean>): Boolean {
            val old = { last: Long? ->
                last == null || clock.now() - Instant.fromEpochMilliseconds(last) > SYNC_INTERVAL
            }
            val music = syncDao.kindState(SyncStateEntity.MUSIC)
            // An install from before ADR 0051 has no music row: its last full sync synced the music.
            val musicLast = if (music == null) syncDao.state()?.lastFullSyncAtMillis else music.lastFullSyncAtMillis
            return (kinds.first && old(musicLast)) ||
                (kinds.second && old(syncDao.kindState(SyncStateEntity.VIDEO)?.lastFullSyncAtMillis))
        }

        private suspend fun join(): Deferred<MqResult<Unit>> =
            lock.withLock {
                running?.takeIf { it.isActive } ?: scope.async { runSync() }.also { running = it }
            }

        /**
         * Cancels any sync, then forgets the library. Not cancellable itself: a half-done wipe would leak data. Holds the
         * lock throughout, so no sync starts until the library is gone: one started meanwhile (a quick sign-in again)
         * would have its first pages wiped and swap in the rest as a partial library (#51).
         */
        private suspend fun wipe() =
            withContext(NonCancellable) {
                lock.withLock {
                    running?.cancelAndJoin()
                    userData.forgetPending()
                    syncDao.clearAll()
                }
                mutableStatus.value = SyncStatus.Idle(null)
            }

        /**
         * Due when never synced, when an interrupted sync can resume, or when a kind of the app mode was last synced over
         * a day ago (a kind left out of the mode for months is not fresh because the other one is).
         */
        internal suspend fun isDue(signedIn: Pair<String, String>): Boolean {
            val state = syncDao.state() ?: return true
            if (state.lastFullSyncAtMillis == null) return true
            if (state.checkpointStep != null && state.belongsTo(signedIn)) return true
            return stale(appMode.mode.first().kinds())
        }

        private suspend fun lastSyncedAt(): Instant? =
            syncDao.state()?.lastFullSyncAtMillis?.let(Instant::fromEpochMilliseconds)

        private suspend fun runSync(): MqResult<Unit> {
            // Signed out meanwhile: nothing to sync, and not a session expiry.
            val account =
                (accountStore.account.first() as? MqResult.Success)?.value
                    ?: return MqResult.Failure(MqError.Unauthorized)
            val startedAt = clock.now()
            log.info(TAG, "Started")
            val result =
                try {
                    fetchAll(signedIn(account))
                } catch (_: SQLiteException) {
                    MqResult.Failure(MqError.Storage)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A bug, not a server answer: it fails this sync only. Thrown on, it would also stop [start] from
                    // following the account, and the next sign-out would leave the library behind (#51).
                    MqResult.Failure(MqError.Unknown(e))
                }
            when (result) {
                is MqResult.Success -> {
                    log.info(TAG, "Done: ${result.value} tracks in ${clock.now() - startedAt}")
                }

                is MqResult.Failure -> {
                    log.warn(
                        TAG,
                        "Failed: ${result.error}",
                        (result.error as? MqError.Unknown)?.cause,
                    )
                }
            }
            // The record first: the card must not show the previous result once the sync shows as finished.
            record(result, startedAt)
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
            return result.map { }
        }

        /** Saves how this run ended for the Sync manager (#148); a cancelled run never gets here. */
        private suspend fun record(
            result: MqResult<Int>,
            startedAt: Instant,
        ) {
            val now = clock.now()
            val record =
                when (result) {
                    is MqResult.Success -> StoredSyncRecord(now, null, result.value, now - startedAt)
                    is MqResult.Failure -> StoredSyncRecord(now, result.error, 0, now - startedAt)
                }
            try {
                accountStore.saveSyncRecord(record)
            } catch (_: IOException) {
                // Only the Sync manager's record is lost; the library itself is fine.
            }
        }

        private suspend fun signedIn(account: StoredAccount) = accountStore.signedInServer(account)

        /** Fetches and swaps in the whole library; the number of tracks it now has. */
        private suspend fun fetchAll(server: SignedInServer): MqResult<Int> {
            val account = server.serverId.value to server.userId
            var state = syncDao.state() ?: SyncStateEntity()
            val now = clock.now().toEpochMilliseconds()
            restartedSteps.clear()
            val (music, video) = appMode.mode.first().kinds()
            runKinds = music to video
            runSteps = SyncStep.entries.filter { if (it.ordinal < SyncStep.Movies.ordinal) music else video }
            // A checkpoint of another mode is not resumed: the swap would replace a kind whose pages were never staged.
            val resumable =
                runSteps.any { it.name == state.checkpointStep } &&
                    state.belongsTo(account) &&
                    now - (state.checkpointStartedAtMillis ?: 0) < RESUME_WINDOW.inWholeMilliseconds &&
                    inCheckpoint(SyncStateEntity.MUSIC, state) == music &&
                    inCheckpoint(SyncStateEntity.VIDEO, state) == video
            if (!resumable) {
                // Offsets drift when the library changes, so an old or foreign checkpoint is not worth resuming.
                syncDao.discardStaging()
                state =
                    SyncStateEntity(
                        lastFullSyncAtMillis = state.lastFullSyncAtMillis,
                        checkpointServerId = account.first,
                        checkpointUserId = account.second,
                        checkpointStartedAtMillis = now,
                        checkpointStep = runSteps.first().name,
                    )
                syncDao.saveState(state)
                markCheckpoint(SyncStateEntity.MUSIC, music, now)
                markCheckpoint(SyncStateEntity.VIDEO, video, now)
            }
            userData.onSyncStarted()
            var step = SyncStep.entries.first { it.name == state.checkpointStep }
            var index = state.checkpointIndex
            // Running from the start, not from the first staged page: the first request can take seconds (#156).
            mutableStatus.value = SyncStatus.Running(step, index, 0)
            log.info(TAG, "Step $step from $index")
            while (true) {
                when (val next = fetchPage(server, state, step, index)) {
                    is MqResult.Failure -> {
                        return next
                    }

                    is MqResult.Success -> {
                        val (nextStep, nextIndex) = next.value ?: break
                        if (nextStep != step) log.info(TAG, "Step $nextStep")
                        step = nextStep
                        index = nextIndex
                    }
                }
            }
            val tracks = syncDao.stagedTrackCount()
            // shortcut: a Video-mode run records 0 tracks in the Sync manager; count videos there when #272 needs it.
            syncDao.swap(clock.now().toEpochMilliseconds(), account.first, account.second, music, video)
            // Pages fetched before a favorite or rating reached the server carry the old value.
            userData.reapplyOverrides()
            return MqResult.Success(tracks)
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
                        stageAlbumGenreLinks(albums.flatMap { it.genreLinks() }.map(::AlbumGenreStaging))
                        stageAlbumLabelLinks(albums.flatMap { it.labelLinks() }.map(::AlbumLabelStaging))
                        stageCreditedArtists(
                            albums.flatMap { it.albumArtists }.map { ArtistStaging(it.toEntity(false)) },
                        )
                    }
                }

                SyncStep.Tracks -> {
                    stagePage(state, step, index, librarySource.tracks(server, index, PAGE_SIZE)) { tracks ->
                        stageTracks(tracks.map { TrackStaging(it.toEntity()) })
                        stageTrackArtistLinks(tracks.flatMap { it.artistLinks() }.map(::TrackArtistStaging))
                        stageTrackGenreLinks(tracks.flatMap { it.genreLinks() }.map(::TrackGenreStaging))
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

                SyncStep.Movies -> {
                    stagePage(state, step, index, librarySource.movies(server, index, PAGE_SIZE)) { stageVideos(it) }
                }

                SyncStep.Series -> {
                    stagePage(state, step, index, librarySource.series(server, index, PAGE_SIZE)) { series ->
                        stageSeries(series.map { SeriesStaging(it.toEntity()) })
                    }
                }

                SyncStep.Seasons -> {
                    stagePage(state, step, index, librarySource.seasons(server, index, PAGE_SIZE)) { seasons ->
                        stageSeasons(seasons.map { SeasonStaging(it.toEntity()) })
                    }
                }

                SyncStep.Episodes -> {
                    stagePage(state, step, index, librarySource.episodes(server, index, PAGE_SIZE)) { stageVideos(it) }
                }
            }

        private suspend fun SyncDao.stageVideos(videos: List<Video>) {
            stageVideos(videos.map { VideoStaging(it.toEntity()) })
            stageVideoStreams(videos.flatMap { it.streamRows() }.map(::VideoStreamStaging))
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
            // The last step keeps its checkpoint until the swap, so a failed swap resumes instead of fetching it all.
            syncDao.stagePage(state.at(next ?: (step to nextIndex))) { write(value.items) }
            if (next?.first != step && stagedCount(step) < value.totalCount && restartedSteps.add(step)) {
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
                SyncStep.Movies -> syncDao.stagedVideoCount(VideoKind.Movie.name)
                SyncStep.Series -> syncDao.stagedSeriesCount()
                SyncStep.Seasons -> syncDao.stagedSeasonCount()
                SyncStep.Episodes -> syncDao.stagedVideoCount(VideoKind.Episode.name)
            }

        private suspend fun SyncDao.clearStaging(step: SyncStep) {
            when (step) {
                SyncStep.AlbumArtists -> {
                    clearArtistStaging()
                }

                SyncStep.Albums -> {
                    clearAlbumStaging()
                    clearAlbumArtistStaging()
                    clearAlbumGenreStaging()
                    clearAlbumLabelStaging()
                }

                SyncStep.Tracks -> {
                    clearTrackStaging()
                    clearTrackArtistStaging()
                    clearTrackGenreStaging()
                }

                SyncStep.Playlists -> {
                    clearPlaylistStaging()
                }

                SyncStep.PlaylistEntries -> {
                    clearPlaylistEntryStaging()
                }

                SyncStep.Movies, SyncStep.Episodes -> {
                    clearVideoStaging(if (step == SyncStep.Movies) VideoKind.Movie.name else VideoKind.Episode.name)
                    clearOrphanVideoStreamStaging()
                }

                SyncStep.Series -> {
                    clearSeriesStaging()
                }

                SyncStep.Seasons -> {
                    clearSeasonStaging()
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
            if (playlistId == null) {
                val next = SyncStep.PlaylistEntries.next()
                // As the last step, the checkpoint stays until the swap, so a failed swap resumes.
                if (next != null) syncDao.stagePage(state.at(next)) {}
                return MqResult.Success(next)
            }
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

        /** The step after this one in [runSteps], `null` after the last. */
        private fun SyncStep.next(): Pair<SyncStep, Int>? =
            runSteps.getOrNull(runSteps.indexOf(this) + 1)?.let {
                it to
                    0
            }

        /** Whether [state]'s pending sync includes the kind of row [id]. */
        private suspend fun inCheckpoint(
            id: Int,
            state: SyncStateEntity,
        ): Boolean =
            syncDao.kindState(id)?.checkpointStartedAtMillis.let {
                it != null &&
                    it == state.checkpointStartedAtMillis
            }

        /** Marks the kind of row [id] as [included] in the sync started at [startedAt], keeping when it last synced. */
        private suspend fun markCheckpoint(
            id: Int,
            included: Boolean,
            startedAt: Long,
        ) {
            val row = syncDao.kindState(id) ?: SyncStateEntity(id)
            syncDao.saveState(row.copy(checkpointStartedAtMillis = startedAt.takeIf { included }))
        }

        private companion object {
            const val TAG = "Sync"

            const val PAGE_SIZE = 500
            val SYNC_INTERVAL = 24.hours
            val RESUME_WINDOW = 1.hours
        }
    }
