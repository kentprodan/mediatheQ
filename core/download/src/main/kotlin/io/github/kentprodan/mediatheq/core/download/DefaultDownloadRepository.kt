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
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.CONVERTED_DOWNLOAD
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.common.DownloadCache
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.DownloadOrigins
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
        private val libraryRepository: LibraryRepository,
        audioPreferences: AudioPreferencesRepository,
        @ApplicationScope private val scope: CoroutineScope,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : DownloadRepository {
        /** Every download by track: the stored index at start, then the manager's changes. */
        private val entries = MutableStateFlow<Map<MediaId, Download>>(emptyMap())
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
                entries.value.keys
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
                entries,
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
                        val converted = cache.getContentMetadata(download.request.id).get(CONVERTED_DOWNLOAD, 0L) == 1L
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

        override val completedIds: Flow<Set<MediaId>> =
            combine(entries, indexRead) { entries, read ->
                if (read) entries.filterValues { it.state == Download.STATE_COMPLETED }.keys else null
            }.filterNotNull().distinctUntilChanged()

        override val freeBytes: Flow<Long> =
            combine(entries, ticks) { _, _ -> StatFs(context.filesDir.path).availableBytes }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        override fun download(
            tracks: List<Track>,
            origin: DownloadOrigin?,
        ) {
            tracks.forEach { requested += it.id }
            if (origin != null) {
                scope.launch(ioDispatcher) { originsLock.withLock { origins.add(tracks.map { it.id }, origin) } }
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
            scope.launch(ioDispatcher) {
                val orphans =
                    originsLock.withLock {
                        val kept = origins.origins.first()
                        origins.removeCollection(origin)
                        kept.filterValues { it == setOf(origin) }.keys.toList()
                    }
                delete(orphans)
            }
        }

        override fun removeSong(id: MediaId) {
            scope.launch(ioDispatcher) {
                val orphan =
                    originsLock.withLock {
                        val left = origins.origins.first()[id].orEmpty() - DownloadOrigin.Single
                        origins.remove(listOf(id), DownloadOrigin.Single)
                        left.isEmpty()
                    }
                if (orphan) delete(listOf(id))
            }
        }

        override fun remove(ids: List<MediaId>) {
            scope.launch(ioDispatcher) { delete(ids) }
        }

        private suspend fun delete(ids: List<MediaId>) {
            if (ids.isEmpty()) return
            originsLock.withLock { origins.forget(ids) }
            ids.forEach {
                DownloadService.sendRemoveDownload(context, MediatheqDownloadService::class.java, it.toString(), false)
            }
        }

        override fun isDownloaded(id: MediaId): Boolean {
            val download =
                entries.value[id]
                    // Right after start (e.g. the player starting the process offline) the index is not read yet.
                    ?: if (loaded) null else manager.downloadIndex.getDownload(id.toString())
            return download?.state == Download.STATE_COMPLETED
        }

        private companion object {
            val PROGRESS_INTERVAL: Duration = 1.seconds

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
