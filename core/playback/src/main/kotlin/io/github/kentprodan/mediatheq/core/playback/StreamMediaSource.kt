package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.ForwardingMediaSourceFactory
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** How a queue item plays: through [streamDataSourceFactory] (download, cache, file, fallback), or as HLS. */
internal sealed interface StreamSource {
    data object Progressive : StreamSource

    /** A converted stream in segments (R15): [url] is its playlist, fetched with [headers] (no token in any URL). */
    data class Hls(
        val url: String,
        val headers: Map<String, String>,
    ) : StreamSource
}

/**
 * Chooses how [itemId] plays before the player builds its source (ADR 0019): HLS only for a segmented stream; a
 * download, a cached original, offline, a failure, a file or a progressive transcode all go through the item's own
 * data source, which handles them (and their retries) as before.
 */
@UnstableApi
internal suspend fun chooseStreamSource(
    itemId: MediaId,
    resolver: StreamResolver,
    downloads: DownloadRepository,
    streamingCache: Cache,
    isOnline: () -> Boolean,
): StreamSource {
    if (downloads.isDownloaded(itemId) || streamingCache.isFullyCached(originalCacheKey(itemId)) || !isOnline()) {
        return StreamSource.Progressive
    }
    val stream = (resolver.resolve(itemId) as? MqResult.Success)?.value ?: return StreamSource.Progressive
    return if (stream.segmented) StreamSource.Hls(stream.url, resolver.headers()) else StreamSource.Progressive
}

/**
 * Builds the player's sources: `mediatheq://` tracks get a [LazyStreamMediaSource]; anything else goes to [progressive].
 * HLS goes straight to the server through the app [okHttpClient] (cleartext rule, ADR 0013), never through a cache:
 * converted segments are not kept (ADR 0018).
 */
@UnstableApi
internal class StreamMediaSourceFactory(
    private val progressive: MediaSource.Factory,
    private val okHttpClient: OkHttpClient,
    private val choose: suspend (MediaId) -> StreamSource,
    private val dispatcher: CoroutineDispatcher,
    /** Called when the player starts loading an HLS track (the prefetcher then leaves it alone). */
    private val onOpen: (MediaId) -> Unit = {},
) : ForwardingMediaSourceFactory(progressive) {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val itemId = mediaItem.localConfiguration?.uri?.trackId() ?: return super.createMediaSource(mediaItem)
        return LazyStreamMediaSource(mediaItem, { choose(itemId) }, { childFor(itemId, mediaItem, it) }, dispatcher)
    }

    private fun childFor(
        itemId: MediaId,
        mediaItem: MediaItem,
        source: StreamSource,
    ): MediaSource =
        when (source) {
            StreamSource.Progressive -> {
                progressive.createMediaSource(mediaItem)
            }

            is StreamSource.Hls -> {
                onOpen(itemId)
                HlsMediaSource
                    .Factory(OkHttpDataSource.Factory(okHttpClient).setDefaultRequestProperties(source.headers))
                    .setLoadErrorHandlingPolicy(StreamLoadErrorPolicy())
                    .createMediaSource(
                        mediaItem
                            .buildUpon()
                            .setUri(source.url)
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .build(),
                    )
            }
        }
}

/**
 * A queue item whose kind of source is known only once its stream is resolved: [choose] runs off the playback thread
 * when the player prepares the item (lazily, like any source), then [childFor] builds the real source. The player's own
 * masking keeps periods waiting until then. The timeline shows [mediaItem] (the `mediatheq://` item), never the
 * resolved URL. Playback thread only, except [choose].
 */
@UnstableApi
internal class LazyStreamMediaSource(
    private val mediaItem: MediaItem,
    private val choose: suspend () -> StreamSource,
    private val childFor: (StreamSource) -> MediaSource,
    private val dispatcher: CoroutineDispatcher,
) : CompositeMediaSource<Unit>() {
    private var child: MediaSource? = null
    private var job: Job? = null

    // Results of an earlier preparation (before a release) are ignored.
    private var generation = 0

    override fun getMediaItem(): MediaItem = mediaItem

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        val handler = Util.createHandlerForCurrentLooper()
        val mine = ++generation
        job =
            CoroutineScope(dispatcher).launch {
                val source =
                    try {
                        choose()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // The item's data source then fails (or retries) the way it always did.
                        StreamSource.Progressive
                    }
                handler.post {
                    if (mine == generation) {
                        val created = childFor(source)
                        child = created
                        prepareChildSource(Unit, created)
                    }
                }
            }
    }

    override fun onChildSourceInfoRefreshed(
        childSourceId: Unit,
        mediaSource: MediaSource,
        newTimeline: Timeline,
    ) = refreshSourceInfo(ItemTimeline(newTimeline, mediaItem))

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long,
    ): MediaPeriod =
        checkNotNull(child) {
            "period before the stream was chosen"
        }.createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        child?.releasePeriod(mediaPeriod)
    }

    override fun releaseSourceInternal() {
        generation++
        job?.cancel()
        job = null
        child = null
        super.releaseSourceInternal()
    }

    /** The child's timeline, with the queue item in place of the resolved one. */
    private class ItemTimeline(
        timeline: Timeline,
        private val item: MediaItem,
    ) : ForwardingTimeline(timeline) {
        override fun getWindow(
            windowIndex: Int,
            window: Window,
            defaultPositionProjectionUs: Long,
        ): Window = super.getWindow(windowIndex, window, defaultPositionProjectionUs).also { it.mediaItem = item }
    }
}
