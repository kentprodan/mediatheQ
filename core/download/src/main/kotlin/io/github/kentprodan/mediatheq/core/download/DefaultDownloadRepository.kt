package io.github.kentprodan.mediatheq.core.download

import android.content.Context
import android.os.StatFs
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.CONVERTED_DOWNLOAD
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.common.DownloadCache
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.CleanupPreview
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.DownloadOrigins
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.DownloadStorageRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [DownloadRepository] on Media3's [DownloadManager]. Each download's id and cache key is its track's [MediaId] string,
 * which is what the player looks up. It has no server dependency at all: removing a download only deletes the local
 * copy (jellyfin R5); the files themselves come through the `@DownloadSource` data source.
 */
@UnstableApi
@Singleton
internal class DefaultDownloadRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val manager: DownloadManager,
        @DownloadCache private val cache: Cache,
        private val origins: DownloadOrigins,
        private val storage: DownloadStorageRepository,
        private val libraryRepository: LibraryRepository,
        session: SessionRepository,
        audioPreferences: AudioPreferencesRepository,
        @ApplicationScope private val scope: CoroutineScope,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        private val log: AppLog,
    ) : DownloadRepository {
        /** Every download by track: the stored index at start, then the manager's changes. */
        private val entries = MutableStateFlow<Map<MediaId, Download>>(emptyMap())

        // The active account's server (#147): other accounts' downloads stay in the shared index, unlisted and held.
        private val activeServer = session.activeServerId

        /** [entries] of the active server; all of them while signed out. */
        private val mine =
            combine(entries, activeServer) { entries, server ->
                if (server == null) entries else entries.filterKeys { it.serverId == server }
            }
        private val notMetRequirements = MutableStateFlow(manager.notMetRequirements)

        // Until the stored index is in [entries], answers come from the index itself, and removals are remembered so
        // the late index read does not bring them back.
        @Volatile private var loaded = false
        private val indexRead = MutableStateFlow(false)
        private val removedBeforeLoad = ConcurrentHashMap.newKeySet<MediaId>()

        // Origin changes read, then write: one at a time, so two quick removals never miss a shared track (#168).
        private val originsLock = Mutex()

        /** Tracks asked for since the app started: their origin is on its way, never guessed. */
        private val requested = ConcurrentHashMap.newKeySet<MediaId>()

        // Failed downloads being started over: a second tap meanwhile must not delete what the new run already wrote.
        private val restarting = ConcurrentHashMap.newKeySet<MediaId>()

        init {
            manager.addListener(
                object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        val id = download.trackId() ?: return
                        when (download.state) {
                            Download.STATE_QUEUED -> {
                                log.info(LOG_TAG, "Queued [${id.itemId}]")
                            }

                            Download.STATE_COMPLETED -> {
                                log.info(
                                    LOG_TAG,
                                    "Done [${id.itemId}], ${download.bytesDownloaded} bytes",
                                )
                            }

                            Download.STATE_FAILED -> {
                                log.warn(LOG_TAG, "Failed [${id.itemId}]", finalException)
                            }
                        }
                        if (download.state != Download.STATE_FAILED) restarting -= id
                        entries.update { it + (id to download) }
                    }

                    override fun onDownloadRemoved(
                        downloadManager: DownloadManager,
                        download: Download,
                    ) {
                        val id = download.trackId() ?: return
                        if (!loaded) removedBeforeLoad += id
                        entries.update { it - id }
                    }

                    override fun onRequirementsStateChanged(
                        downloadManager: DownloadManager,
                        requirements: Requirements,
                        notMetRequirements: Int,
                    ) {
                        this@DefaultDownloadRepository.notMetRequirements.value = notMetRequirements
                    }
                },
            )
            // On the main thread, where the manager lives. Not through the service: Android 12+ refuses to start it
            // from the background.
            scope.launch(Dispatchers.Main.immediate) {
                audioPreferences.preferences
                    .map { it.downloadsUnmeteredOnly }
                    .distinctUntilChanged()
                    .collect { manager.requirements = DownloadModule.requirements(it) }
            }
            // Settings -> Offline, cache, and download -> Media (#144); lowering it lets running downloads finish.
            scope.launch(Dispatchers.Main.immediate) {
                audioPreferences.preferences
                    .map { it.maxParallelDownloads }
                    .distinctUntilChanged()
                    .collect { manager.maxParallelDownloads = it }
            }
            scope.launch(ioDispatcher) {
                val stored =
                    manager.downloadIndex.getDownloads().use { cursor ->
                        buildMap {
                            while (cursor.moveToNext()) {
                                val download = cursor.download
                                download.trackId()?.let { put(it, download) }
                            }
                        }
                    }
                // Changes the manager reported meanwhile are newer than the index.
                entries.update { (stored - removedBeforeLoad) + it }
                loaded = true
                indexRead.value = true
                originless()
            }
            scope.launch { activeServer.collect { holdOtherAccounts(it) } }
        }

        // Progress is not a manager event; while someone watches, read it once a second.
        private val ticks =
            flow {
                while (true) {
                    emit(Unit)
                    delay(PROGRESS_INTERVAL)
                }
            }

        /**
         * Downloads from before #168 have no origin: they count as their album's (the Downloads tab grouped them so), or
         * alone without one. Written for those still without one, so a later playlist download of the same track keeps
         * the album too. Not before the library is read: without it every download would count as alone.
         */
        private suspend fun originless() =
            originsLock.withLock {
                val known = origins.origins.first()
                val tracks = libraryRepository.allTracks().first().associateBy { it.id }
                if (tracks.isEmpty()) return@withLock
                mine
                    .first()
                    .keys
                    .filter { it !in known && it !in requested }
                    .groupBy { tracks[it]?.albumId }
                    .forEach { (albumId, ids) ->
                        origins.add(
                            ids,
                            albumId?.let(DownloadOrigin::Album) ?: DownloadOrigin.Single,
                        )
                    }
            }

        override val downloads: Flow<List<TrackDownload>> =
            combine(
                mine,
                notMetRequirements,
                ticks,
                libraryRepository.allTracks().map { tracks -> tracks.associateBy { it.id } },
                origins.origins,
            ) { entries, notMet, _, tracks, origins ->
                val current = manager.currentDownloads.associateBy { it.request.id }
                entries
                    .mapNotNull { (id, download) ->
                        val live = current[download.request.id] ?: download
                        val state = downloadState(live.state, live.percentDownloaded, notMet) ?: return@mapNotNull null
                        // The conversion's bitrate (#144), or 1 before; 0 = the original.
                        val converted = cache.getContentMetadata(download.request.id).get(CONVERTED_DOWNLOAD, 0L) != 0L
                        TrackDownload(
                            tracks[id] ?: download.fallbackTrack(id),
                            state,
                            converted,
                            live.bytesDownloaded,
                            origins[id].orEmpty(),
                        )
                    }.sortedWith(ALBUM_ORDER)
            }.distinctUntilChanged()
                .flowOn(defaultDispatcher)

        // With the SD card out (#146) none is on the device: offline lists them as missing, online they stream.
        override val completedIds: Flow<Set<MediaId>> =
            combine(mine, indexRead, storage.available) { entries, read, available ->
                when {
                    !read -> null
                    !available -> emptySet()
                    else -> entries.filterValues { it.state == Download.STATE_COMPLETED }.keys
                }
            }.filterNotNull().distinctUntilChanged()

        override val freeBytes: Flow<Long> =
            combine(entries, ticks, storage.available) { _, _, available ->
                if (available) StatFs(storage.filesDir.path).availableBytes else 0L
            }.distinctUntilChanged()
                .flowOn(ioDispatcher)

        override fun download(
            tracks: List<Track>,
            origin: DownloadOrigin?,
        ) {
            tracks.forEach { requested += it.id }
            if (origin != null) {
                // Undispatched: origin changes take the (fair) lock in call order, so a favorite added and a
                // collection removed in one pass (#145) never delete the track the favorite keeps.
                scope.launch(ioDispatcher, CoroutineStart.UNDISPATCHED) {
                    originsLock.withLock { origins.add(tracks.map { it.id }, origin) }
                }
            }
            val known = entries.value
            tracks
                // Downloading again would restart a finished or running download; only failed ones retry.
                .filter { known[it.id]?.state.let { state -> state == null || state == Download.STATE_FAILED } }
                .forEach { track ->
                    if (known[track.id]?.state != Download.STATE_FAILED) {
                        enqueue(track)
                    } else if (restarting.add(track.id)) {
                        scope.launch(ioDispatcher) {
                            // Its partial copy cannot always go on (a conversion never can, #68): it starts over.
                            cache.removeResource(track.id.toString())
                            enqueue(track)
                        }
                    }
                }
        }

        private fun enqueue(track: Track) {
            val key = track.id.toString()
            val request =
                DownloadRequest
                    .Builder(key, DOWNLOAD_URI)
                    .setCustomCacheKey(key)
                    .setData(track.title.encodeToByteArray())
                    .build()
            DownloadService.sendAddDownload(context, MediatheqDownloadService::class.java, request, false)
        }

        override fun removeCollection(origin: DownloadOrigin) {
            // Songs go one by one (removeSong): "every song" is no collection to remove.
            if (origin == DownloadOrigin.Single) return
            scope.launch(ioDispatcher, CoroutineStart.UNDISPATCHED) {
                val orphans =
                    originsLock.withLock {
                        val kept = origins.origins.first()
                        origins.removeCollection(origin)
                        kept.filterValues { it == setOf(origin) }.keys.toList()
                    }
                delete(orphans)
            }
        }

        override fun removeOrigin(
            ids: List<MediaId>,
            origin: DownloadOrigin,
        ) {
            scope.launch(ioDispatcher, CoroutineStart.UNDISPATCHED) {
                val orphans =
                    originsLock.withLock {
                        val kept = origins.origins.first()
                        origins.remove(ids, origin)
                        ids.filter { (kept[it].orEmpty() - origin).isEmpty() }
                    }
                delete(orphans)
            }
        }

        override fun remove(ids: List<MediaId>) {
            scope.launch(ioDispatcher) { delete(ids) }
        }

        override suspend fun removeServer(serverId: ServerId) {
            indexRead.first { it }
            val ids = entries.value.keys.filter { it.serverId == serverId }
            if (ids.isEmpty()) return
            delete(ids)
            // The app may restart next (the active account removed): wait until the index forgot them.
            withTimeoutOrNull(REMOVE_TIMEOUT) { entries.first { current -> ids.none { it in current } } }
        }

        /**
         * Downloads of another account's server cannot run (the stream would be asked from the active server): they wait
         * with [OTHER_ACCOUNT] as stop reason until their account is active again (#147).
         */
        private suspend fun holdOtherAccounts(active: ServerId?) {
            if (active == null) return
            indexRead.first { it }
            // The stored stop reasons: the manager's events may not have reached [entries] yet.
            val changes =
                withContext(ioDispatcher) {
                    entries.value.keys.mapNotNull { id ->
                        val download = manager.downloadIndex.getDownload(id.toString()) ?: return@mapNotNull null
                        if (download.state == Download.STATE_COMPLETED || download.state == Download.STATE_FAILED) {
                            return@mapNotNull null
                        }
                        val reason = if (id.serverId == active) Download.STOP_REASON_NONE else OTHER_ACCOUNT
                        val change =
                            download.stopReason != reason && OTHER_ACCOUNT in setOf(reason, download.stopReason)
                        if (change) download.request.id to reason else null
                    }
                }
            withContext(Dispatchers.Main.immediate) {
                changes.forEach { (id, reason) ->
                    manager.setStopReason(id, reason)
                }
            }
        }

        private suspend fun delete(ids: List<MediaId>) {
            if (ids.isEmpty()) return
            originsLock.withLock { origins.forget(ids) }
            ids.forEach {
                DownloadService.sendRemoveDownload(context, MediatheqDownloadService::class.java, it.toString(), false)
            }
        }

        override val paused: Flow<Boolean> = storage.pausedByUser

        override fun pauseAll() = storage.setPausedByUser(true)

        override fun resumeAll() = storage.setPausedByUser(false)

        override suspend fun cleanupPreview(): CleanupPreview =
            withContext(ioDispatcher) {
                val (selection, downloads) = selectCleanup()
                val failedBytes = selection.failed.sumOf { downloads[it.toString()]?.bytesDownloaded ?: 0 }
                CleanupPreview(selection.failed.size, failedBytes + selection.orphans.sumOf { cache.cachedBytes(it) })
            }

        override suspend fun cleanup() {
            val (selection, _) = withContext(ioDispatcher) { selectCleanup() }
            // A failed download the user just asked again for is starting over: keep it.
            delete(selection.failed.filter { it !in restarting })
            // The manager's own list, read on its thread.
            val running =
                withContext(Dispatchers.Main.immediate) { manager.currentDownloads.map { it.request.id }.toSet() }
            withContext(ioDispatcher) {
                selection.orphans.filter { it !in running && stillOrphan(it) }.forEach(cache::removeResource)
            }
        }

        /** The selection now, from the stored index (every server) and the download cache; nothing while the card is out. */
        private suspend fun selectCleanup(): Pair<CleanupSelection, Map<String, Download>> {
            indexRead.first { it }
            if (!storage.available.value) return CleanupSelection(emptyList(), emptyList()) to emptyMap()
            val downloads =
                manager.downloadIndex.getDownloads().use { cursor ->
                    buildMap { while (cursor.moveToNext()) cursor.download.let { put(it.request.id, it) } }
                }
            val active = activeServer.first()
            return cleanupSelection(downloads.mapValues { it.value.state }, cache.keys, active) to downloads
        }

        /** Checked right before deleting: a download added or restarted since the selection keeps its data. */
        private fun stillOrphan(key: String): Boolean {
            val id = runCatching { MediaId.parse(key) }.getOrNull()
            return manager.downloadIndex.getDownload(key) == null &&
                id !in requested &&
                id !in restarting
        }

        override fun isDownloaded(id: MediaId): Boolean {
            if (!storage.available.value) return false
            val download =
                entries.value[id]
                    // Right after start (e.g. the player starting the process offline) the index is not read yet.
                    ?: if (loaded) null else manager.downloadIndex.getDownload(id.toString())
            return download?.state == Download.STATE_COMPLETED
        }

        private companion object {
            val PROGRESS_INTERVAL: Duration = 1.seconds
            val REMOVE_TIMEOUT: Duration = 10.seconds

            /** The stop reason of a download whose account is not active. */
            const val OTHER_ACCOUNT = 147

            /** Not fetched: the download source finds the track by the request's cache key. */
            val DOWNLOAD_URI = "mediatheq://download".toUri()

            val ALBUM_ORDER =
                compareBy<TrackDownload>(
                    { it.track.albumTitle ?: "" },
                    { it.track.albumId?.toString() ?: "" },
                    { it.track.discNumber ?: 0 },
                    { it.track.trackNumber ?: 0 },
                    { it.track.sortName },
                )
        }
    }

@UnstableApi
private fun Download.trackId(): MediaId? = runCatching { MediaId.parse(request.id) }.getOrNull()

/**
 * A download whose track left the library (deleted on the server) still plays and can be removed; only its title is
 * known, from the request.
 * shortcut: title only, no album or artists; store more in the request data if such downloads turn out common.
 */
@UnstableApi
private fun Download.fallbackTrack(id: MediaId) =
    Track(
        id = id,
        title =
            request.data.decodeToString().ifEmpty {
                id.itemId
            },
        artists = emptyList(),
        duration = Duration.ZERO,
    )

/** Bytes of [key] in this cache, whatever spans it has. */
@UnstableApi
private fun Cache.cachedBytes(key: String): Long = getCachedSpans(key).sumOf { it.length }

private const val LOG_TAG = "Download"
