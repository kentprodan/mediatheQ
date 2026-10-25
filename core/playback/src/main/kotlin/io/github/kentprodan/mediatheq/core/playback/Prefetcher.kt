package io.github.kentprodan.mediatheq.core.playback

import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches the next tracks in play order while the current one plays (media3 skill, section 9; ADR 0018): two on an
 * unmetered network, one on a metered one, original files only (converted streams cannot be cached). Nothing happens
 * while paused, before a restored queue is played, or offline. Runs its decisions on the player's thread.
 */
@UnstableApi
internal class Prefetcher(
    private val player: Player,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val resolver: StreamResolver,
    private val downloads: DownloadRepository,
    private val cache: Cache,
    private val upstream: DataSource.Factory,
    private val network: NetworkMonitor,
) : Player.Listener {
    @Volatile private var job: Job? = null
    private var targets: List<MediaId> = emptyList()

    /** The track being written right now and the job writing it, so the player can take it over ([cancel]). */
    @Volatile private var writing: Pair<MediaId, Job>? = null

    /** Tracks the player started loading itself since the last transition: never fetched a second time. */
    private val taken = ConcurrentHashMap.newKeySet<MediaId>()

    fun start(): Job {
        player.addListener(this)
        // Coming back online is a reason to look again; the first value is the current state.
        // shortcut: a move from cellular to Wi-Fi is noticed at the next player event, not at once.
        return scope.launch { network.online.drop(1).collect { restart() } }
    }

    /** Stops for good (service destroyed): no download goes on without a player. */
    fun stop() {
        player.removeListener(this)
        job?.cancel()
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
            )
        ) {
            // The player's own loads before a transition (the next track, for gapless) are now behind it.
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) taken.clear()
            restart()
        }
    }

    /**
     * Stops writing [itemId]: the player is about to load it itself, and two writers of one track would each fetch it
     * (Media3 lets the second one read from the server without caching). Safe from the loader thread.
     */
    fun cancel(itemId: MediaId) {
        taken += itemId
        writing?.let { (id, writer) -> if (id == itemId) writer.cancel() }
    }

    private fun restart() {
        val active = player.playWhenReady && player.playbackState != Player.STATE_IDLE && network.isOnline()
        val next = if (active) upcoming(if (network.isMetered()) METERED_COUNT else UNMETERED_COUNT) else emptyList()
        // Events come in bursts (start, buffering, ready): a running prefetch of the same tracks goes on.
        if (next == targets && job?.isActive == true) return
        job?.cancel()
        targets = next
        if (next.isNotEmpty()) job = scope.launch { next.forEach { prefetch(it) } }
    }

    /** The next [count] tracks in play order (shuffle and repeat included), never the current one again. */
    private fun upcoming(count: Int): List<MediaId> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val current = player.currentMediaItem?.mediaId
        // Repeat one would only find the current track; the queue's real successors are worth more.
        val repeatMode = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
        val result = mutableListOf<MediaId>()
        var index = player.currentMediaItemIndex
        repeat(timeline.windowCount) {
            if (result.size == count) return result
            index = timeline.getNextWindowIndex(index, repeatMode, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET) return result
            val item = player.getMediaItemAt(index)
            val id = runCatching { MediaId.parse(item.mediaId) }.getOrNull()
            if (id != null && item.mediaId != current && id !in result && id !in taken) result += id
        }
        return result
    }

    private suspend fun prefetch(itemId: MediaId) =
        withContext(ioDispatcher) {
            val key = originalCacheKey(itemId)
            // Checked first, so no play session is opened on the server for nothing.
            if (downloads.isDownloaded(itemId) || cache.isFullyCached(key)) return@withContext
            val stream = (resolver.prefetchStream(itemId) as? MqResult.Success)?.value ?: return@withContext
            if (stream.playMethod != PlayMethod.DirectPlay) return@withContext
            cache.dropIfChanged(key, stream.sizeBytes)
            val spec =
                DataSpec
                    .Builder()
                    .setUri(stream.url.toUri())
                    .setKey(key)
                    .setHttpRequestHeaders(resolver.headers())
                    .setFlags(DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN)
                    .build()
            val writer = CacheWriter(CacheDataSource(cache, upstream.createDataSource()), spec, null, null)
            val mine = itemId to coroutineContext.job
            writing = mine
            try {
                // The player may have opened it while the stream was being resolved.
                if (itemId in taken) return@withContext
                // CacheWriter blocks; cancelling the job interrupts it.
                runInterruptible { writer.cache() }
            } catch (_: InterruptedIOException) {
                // Cancelled mid-transfer (pause, skip, the player taking over): nothing to report.
            } catch (e: IOException) {
                // The player loads the track itself later; nothing is lost.
                Log.w(TAG, "Prefetch of $itemId failed", e)
            } finally {
                writer.cancel()
                // A newer prefetch may already be writing; its entry stays.
                if (writing === mine) writing = null
            }
        }

    private companion object {
        const val TAG = "Prefetcher"
        const val UNMETERED_COUNT = 2
        const val METERED_COUNT = 1
    }
}
