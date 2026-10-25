package io.github.kentprodan.mediatheq.core.playback

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Data source for the player: `mediatheq://track/...` becomes the server's stream URL with the auth header, built at
 * load time, or the downloaded copy when the track is fully downloaded (no network at all). All requests go through
 * the app [okHttpClient], so the local-network cleartext rule applies (ADR 0013).
 */
@UnstableApi
internal fun streamDataSourceFactory(
    okHttpClient: OkHttpClient,
    resolver: StreamResolver,
    downloads: DownloadRepository,
    downloadCache: Cache,
    isOnline: () -> Boolean,
): DataSource.Factory {
    // Read-only: streams are never written into the download cache, and only downloads have a key that can hit.
    val cached =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(okHttpClient))
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory { it.key ?: STREAM_CACHE_KEY }
    return ResolvingDataSource.Factory(cached) { dataSpec ->
        val itemId = dataSpec.uri.trackId() ?: return@Factory dataSpec
        if (downloads.isDownloaded(itemId)) {
            resolver.playLocal(itemId)
            return@Factory dataSpec.buildUpon().setKey(downloadCacheKey(itemId)).build()
        }
        // Offline only downloads play; anything else fails at once, without a server request or retries.
        if (!isOnline()) throw OfflineException()
        resolveStream(dataSpec, itemId, resolver) { resolver.resolve(itemId, continuing = dataSpec.position > 0) }
    }
}

/**
 * Data source for downloads: a download's cache key (see [downloadCacheKey]) becomes the original file's URL with the
 * auth header. Downloads never touch the player's streams.
 */
@UnstableApi
internal fun downloadDataSourceFactory(
    okHttpClient: OkHttpClient,
    resolver: StreamResolver,
): DataSource.Factory =
    ResolvingDataSource.Factory(OkHttpDataSource.Factory(okHttpClient)) { dataSpec ->
        val itemId =
            dataSpec.key?.let { runCatching { MediaId.parse(it) }.getOrNull() }
                ?: throw StreamUnavailableException(MqError.Client(0, "download: no track id"))
        resolveStream(dataSpec, itemId, resolver) { resolver.downloadStream(itemId) }
    }

/**
 * A download's key in the download cache: its track id. Downloads (`:core:download`) use [MediaId.toString] as
 * request id and custom cache key; this is the same value, named for the player side.
 */
internal fun downloadCacheKey(itemId: MediaId): String = itemId.toString()

// Lookups of streamed tracks never hit (no download has this key) and keep stream URLs out of cache keys.
private const val STREAM_CACHE_KEY = "stream"

@UnstableApi
private fun resolveStream(
    dataSpec: DataSpec,
    itemId: MediaId,
    resolver: StreamResolver,
    resolve: suspend () -> MqResult<PlaybackStream>,
): DataSpec =
    // Runs on the loader thread, which may block (ResolvingDataSource.Resolver contract).
    runBlocking {
        val stream =
            when (val result = resolve()) {
                is MqResult.Success -> result.value
                is MqResult.Failure -> throw StreamUnavailableException(result.error)
            }
        dataSpec.withUri(stream.url.toUri()).withAdditionalHeaders(resolver.headers())
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
