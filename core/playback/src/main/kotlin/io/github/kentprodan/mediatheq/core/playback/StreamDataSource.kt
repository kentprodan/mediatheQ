package io.github.kentprodan.mediatheq.core.playback

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Data source for the player: `mediatheq://track/...` becomes the server's stream URL with the auth header, built at
 * load time. All requests go through the app [okHttpClient], so the local-network cleartext rule applies (ADR 0013).
 */
@UnstableApi
internal fun streamDataSourceFactory(
    okHttpClient: OkHttpClient,
    resolver: StreamResolver,
): DataSource.Factory =
    ResolvingDataSource.Factory(OkHttpDataSource.Factory(okHttpClient)) { dataSpec ->
        val itemId = dataSpec.uri.trackId() ?: return@Factory dataSpec
        // Runs on the loader thread, which may block (ResolvingDataSource.Resolver contract).
        runBlocking {
            val stream =
                when (val result = resolver.resolve(itemId, continuing = dataSpec.position > 0)) {
                    is MqResult.Success -> result.value
                    is MqResult.Failure -> throw StreamUnavailableException(result.error)
                }
            dataSpec.withUri(stream.url.toUri()).withAdditionalHeaders(resolver.headers())
        }
    }

/** The server would not give a stream for a track (signed out, no compatible format, unreachable). */
class StreamUnavailableException(
    val error: MqError,
) : IOException("Stream unavailable: $error")

/**
 * Retries network trouble like the default policy, but not answers that will not change (signed out, refused, no
 * compatible stream): each retry would ask the server for a new play session for nothing.
 */
@UnstableApi
internal class StreamLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = (loadErrorInfo.exception as? StreamUnavailableException)?.error
        return if (error != null && error != MqError.Network && error !is MqError.Server) {
            C.TIME_UNSET
        } else {
            super.getRetryDelayMsFor(loadErrorInfo)
        }
    }
}
