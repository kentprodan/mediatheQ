package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.data.WaveformRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Where the whole file of a track is on the device (#124): a completed download (original or converted, its size as
 * stored), else a complete original in the streaming cache. `null` for a partial copy or a converted stream (never
 * cached, ADR 0018).
 */
@UnstableApi
internal class LocalFiles(
    private val completedDownloads: Flow<Set<MediaId>>,
    private val downloadCache: Cache,
    private val streamingCache: Cache,
) {
    suspend fun find(id: MediaId): LocalFile? {
        if (id in completedDownloads.first()) {
            val key = downloadCacheKey(id)
            // A converted download has no content length; its stored bytes are its size.
            val size = downloadCache.getCachedBytes(key, 0, C.LENGTH_UNSET.toLong())
            if (size > 0) return LocalFile(downloadCache, key, size)
        }
        val key = originalCacheKey(id)
        if (!streamingCache.isFullyCached(key)) return null
        return LocalFile(streamingCache, key, ContentMetadata.getContentLength(streamingCache.getContentMetadata(key)))
    }
}

/**
 * Computes waveforms on the device (#124, ADR 0027), one file at a time on [worker] (a background thread): the
 * current track and the next two once their file is whole on the device, and downloads finishing while the service
 * runs. A result is stored per file size, so a replay shows it at once and a changed file is analyzed again. No
 * backlog: a track missed (process death, a failure) is tried again the next time it is queued.
 */
@UnstableApi
internal class WaveformAnalyzer(
    private val scope: CoroutineScope,
    private val worker: CoroutineDispatcher,
    private val files: suspend (MediaId) -> LocalFile?,
    private val decoder: PcmDecoder,
    private val waveforms: WaveformRepository,
    private val completedDownloads: Flow<Set<MediaId>>,
    private val log: AppLog = AppLog.None,
) {
    private val pending = ArrayDeque<MediaId>()

    /** Files that failed this session (by key and size): not decoded again on every player event. */
    private val failed = mutableSetOf<Pair<String, Long>>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Watches [player]: the current track and the next ones, again whenever loading stops (a file may be whole). */
    val playerListener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_IS_LOADING_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                    )
                ) {
                    offer(listOfNotNull(player.currentTrackId()) + player.upcomingTracks(NEXT_COUNT), first = true)
                }
            }
        }

    /** Queues [ids]; [first] puts them ahead of everything, in this order (what plays now and next). Any thread. */
    fun offer(
        ids: List<MediaId>,
        first: Boolean = false,
    ) {
        if (ids.isEmpty()) return
        synchronized(pending) {
            if (first) {
                pending.removeAll(ids.toSet())
                ids.asReversed().forEach { pending.addFirst(it) }
            } else {
                ids.filterNot { it in pending }.forEach { pending.addLast(it) }
            }
        }
        wake.trySend(Unit)
    }

    fun start(): Job =
        scope.launch(worker) {
            launch {
                // Only downloads finishing from now on: the ones already there wait until they are queued. An empty set
                // (the SD card out) is no start to count from, or every download would look new when it comes back.
                var known: Set<MediaId>? = null
                completedDownloads.collect { ids ->
                    known?.takeIf { it.isNotEmpty() }?.let { offer((ids - it).toList()) }
                    known = ids
                }
            }
            while (true) {
                wake.receive()
                while (true) {
                    val id = synchronized(pending) { pending.removeFirstOrNull() } ?: break
                    // One track's trouble (a database error, a broken file) must not end the analysis for the rest.
                    try {
                        analyze(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.warn(TAG, "Waveform of $id failed", e)
                    }
                }
            }
        }

    internal suspend fun analyze(id: MediaId) {
        val file = files(id) ?: return
        val attempt = file.key to file.sizeBytes
        if (attempt in failed || waveforms.isAnalyzed(id, file.sizeBytes)) return
        val started = TimeSource.Monotonic.markNow()
        log.info(TAG, "Decoding $id (${file.key}, ${file.sizeBytes} bytes)")
        // The backstop for a decode that never ends (#188): it cannot hold the worker, and the queue, forever.
        val result = withTimeoutOrNull(decodeLimit(file.sizeBytes)) { decoder.decode(file) } ?: Decoded.Failed
        log.info(TAG, "Decoded $id: ${result.name} in ${started.elapsedNow().inWholeMilliseconds} ms")
        when (result) {
            is Decoded.Levels -> waveforms.save(id, file.sizeBytes, result.levels)

            Decoded.Unsupported -> waveforms.save(id, file.sizeBytes, null)

            // Tried again in a later session (after process death, or when the service starts again).
            Decoded.Failed -> failed += attempt
        }
    }

    private val Decoded.name: String
        get() =
            when (this) {
                is Decoded.Levels -> "levels"
                Decoded.Unsupported -> "unsupported"
                Decoded.Failed -> "failed"
            }

    private companion object {
        const val TAG = "MqWaveform"
        const val NEXT_COUNT = 2
        val MIN_DECODE_LIMIT = 2.minutes

        /** At least [MIN_DECODE_LIMIT], one more second per MB: a long mix on a slow phone still finishes. */
        fun decodeLimit(sizeBytes: Long): Duration = MIN_DECODE_LIMIT + (sizeBytes / BYTES_PER_MB).seconds

        const val BYTES_PER_MB = 1_000_000L
    }
}
