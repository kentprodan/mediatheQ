package io.github.kentprodan.mediatheq.core.playback

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.github.kentprodan.mediatheq.core.common.CONVERTED_DOWNLOAD
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Data source for the player: `mediatheq://track/...` plays, in this order, from the download, from a complete copy in
 * the streaming cache (both with no network at all), or from the server's stream URL with the auth header, built at
 * load time and kept in the streaming cache when it is the original file. All requests go through the app
 * [okHttpClient], so the local-network cleartext rule applies (ADR 0013).
 */
@UnstableApi
internal fun streamDataSourceFactory(
    okHttpClient: OkHttpClient,
    resolver: StreamResolver,
    downloads: DownloadRepository,
    downloadCache: Cache,
    streamingCache: Cache,
    isOnline: () -> Boolean,
    /** Called when the player starts loading a track (the prefetcher then leaves it alone). */
    onOpen: (MediaId) -> Unit = {},
): DataSource.Factory {
    val streamed =
        CacheDataSource
            .Factory()
            .setCache(streamingCache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(okHttpClient))
    // Read-only: streams are never written into the download cache, and only downloads have a key that can hit.
    val cached =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(streamed)
            .setCacheWriteDataSinkFactory(null)
    return ResolvingDataSource.Factory(cached) { dataSpec ->
        val itemId = dataSpec.uri.trackId() ?: return@Factory dataSpec
        onOpen(itemId)
        if (downloads.isDownloaded(itemId)) {
            resolver.playLocal(itemId)
            return@Factory dataSpec.buildUpon().setKey(downloadCacheKey(itemId)).build()
        }
        val original = originalCacheKey(itemId)
        // shortcut: a complete copy plays as it is, even if the file changed on the server since; a partial replay
        // (after eviction) notices the new size and starts over.
        if (streamingCache.isFullyCached(original)) {
            resolver.playLocal(itemId)
            return@Factory dataSpec.buildUpon().setKey(original).build()
        }
        // Offline only downloads and cached tracks play; anything else fails at once, without retries.
        if (!isOnline()) throw OfflineException()
        // The item's source chose its stream when the player prepared it (LazyStreamMediaSource): reopening reuses
        // it, so a replay after a network change never hands this file source another format.
        val stream = awaitStream { resolver.resolve(itemId, continuing = true) }
        // Only when the choice failed and a later resolve gave HLS: a playlist is not a file.
        if (stream.segmented) throw StreamUnavailableException(MqError.Client(0, "segmented stream in a file source"))
        val key = streamCacheKey(stream)
        if (stream.playMethod == PlayMethod.DirectPlay) streamingCache.dropIfChanged(key, stream.sizeBytes)
        dataSpec
            .buildUpon()
            .setUri(stream.url.toUri())
            .setKey(key)
            .build()
            .withAdditionalHeaders(runBlocking { resolver.headers() })
    }
}

/**
 * Data source for downloads: a download's cache key (see [downloadCacheKey]) becomes the URL of the original file, or of
 * its conversion when the device cannot play it (#68), with the auth header; [cache] (the download cache) records which
 * one in the download's [CONVERTED_DOWNLOAD] metadata. Downloads never touch the player's streams.
 */
@UnstableApi
internal fun downloadDataSourceFactory(
    okHttpClient: OkHttpClient,
    resolver: StreamResolver,
    cache: Cache,
): DataSource.Factory =
    ResolvingDataSource.Factory(OkHttpDataSource.Factory(okHttpClient)) { dataSpec ->
        val key =
            dataSpec.key ?: throw StreamUnavailableException(MqError.Client(0, "download: no track id"))
        val itemId =
            runCatching { MediaId.parse(key) }.getOrNull()
                ?: throw StreamUnavailableException(MqError.Client(0, "download: no track id"))
        val stream = awaitStream { resolver.downloadStream(itemId) }
        val converted = stream.playMethod != PlayMethod.DirectPlay
        // A conversion is made anew by each request, with no length and no ranges (R12): it never continues a partial
        // copy, and an original never continues a converted one. Downloading again starts over.
        if (dataSpec.position > 0 && (converted || cache.isConvertedDownload(key))) {
            throw StreamUnavailableException(MqError.Client(0, "download: a converted copy cannot continue"))
        }
        cache.applyContentMetadataMutations(
            key,
            ContentMetadataMutations().set(CONVERTED_DOWNLOAD, if (converted) 1L else 0L),
        )
        dataSpec.withUri(stream.url.toUri()).withAdditionalHeaders(runBlocking { resolver.headers() })
    }

/** Whether the download [key] in this download cache is (or began as) the server's conversion. */
@UnstableApi
internal fun Cache.isConvertedDownload(key: String): Boolean = getContentMetadata(key).get(CONVERTED_DOWNLOAD, 0L) == 1L

/**
 * A download's key in the download cache: its track id. Downloads (`:core:download`) use [MediaId.toString] as
 * request id and custom cache key; this is the same value, named for the player side.
 */
internal fun downloadCacheKey(itemId: MediaId): String = itemId.toString()

// Runs on the loader thread, which may block (ResolvingDataSource.Resolver contract).
private fun awaitStream(resolve: suspend () -> MqResult<PlaybackStream>): PlaybackStream =
    when (val result = runBlocking { resolve() }) {
        is MqResult.Success -> result.value
        is MqResult.Failure -> throw StreamUnavailableException(result.error)
    }

/** The server would not give a stream for a track (signed out, no compatible format, unreachable). */
class StreamUnavailableException(
    val error: MqError,
) : IOException("Stream unavailable: $error")

/** The device was offline when a track that is not downloaded was to load. */
class OfflineException : IOException("Offline: only downloads play")

/**
 * Retries network trouble like the default policy (a dropped connection during a Wi-Fi to cellular handover comes
 * back), but not answers that will not change (signed out, refused, no compatible stream: each retry would ask the
 * server for a new play session for nothing), nor a track that cannot load offline.
 */
@UnstableApi
internal class StreamLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val exception = loadErrorInfo.exception
        val error = (exception as? StreamUnavailableException)?.error
        return if (exception is OfflineException ||
            (error != null && error != MqError.Network && error !is MqError.Server)
        ) {
            C.TIME_UNSET
        } else {
            super.getRetryDelayMsFor(loadErrorInfo)
        }
    }
}
