package io.github.kentprodan.mediatheq.core.download

import android.content.Context
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
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.common.DownloadCache
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
        libraryRepository: LibraryRepository,
        audioPreferences: AudioPreferencesRepository,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
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

        init {
            manager.addListener(
                object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        val id = download.trackId() ?: return
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

        override val downloads: Flow<List<TrackDownload>> =
            combine(
                entries,
                notMetRequirements,
                ticks,
                libraryRepository.allTracks().map { tracks -> tracks.associateBy { it.id } },
            ) { entries, notMet, _, tracks ->
                val current = manager.currentDownloads.associateBy { it.request.id }
                entries
                    .mapNotNull { (id, download) ->
                        val live = current[download.request.id] ?: download
                        val state = downloadState(live.state, live.percentDownloaded, notMet) ?: return@mapNotNull null
                        TrackDownload(tracks[id] ?: download.fallbackTrack(id), state)
                    }.sortedWith(ALBUM_ORDER)
            }.distinctUntilChanged()
                .flowOn(defaultDispatcher)

        override val completedIds: Flow<Set<MediaId>> =
            combine(entries, indexRead) { entries, read ->
                if (read) entries.filterValues { it.state == Download.STATE_COMPLETED }.keys else null
            }.filterNotNull().distinctUntilChanged()

        override val storageUsed: Flow<Long> =
            combine(entries, ticks) { _, _ -> cache.cacheSpace }.distinctUntilChanged().flowOn(defaultDispatcher)

        override fun download(tracks: List<Track>) {
            val known = entries.value
            tracks
                // Downloading again would restart a finished or running download; only failed ones retry.
                .filter { known[it.id]?.state.let { state -> state == null || state == Download.STATE_FAILED } }
                .forEach { track ->
                    val key = track.id.toString()
                    val request =
                        DownloadRequest
                            .Builder(key, DOWNLOAD_URI)
                            .setCustomCacheKey(key)
                            .setData(track.title.encodeToByteArray())
                            .build()
                    DownloadService.sendAddDownload(context, MediatheqDownloadService::class.java, request, false)
                }
        }

        override fun remove(ids: List<MediaId>) {
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
