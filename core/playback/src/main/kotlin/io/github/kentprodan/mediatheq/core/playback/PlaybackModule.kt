package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.kentprodan.mediatheq.core.common.DownloadSource
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
internal interface PlaybackModule {
    @Binds
    fun playbackConnection(connection: MediaControllerPlaybackConnection): PlaybackConnection

    @Binds
    fun audioDecoders(decoders: MediaCodecAudioDecoders): AudioDecoders
}

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
internal object PlaybackCachesModule {
    /**
     * In cacheDir: the system may clear it under storage pressure, which only costs fetching the files again. The
     * Media3 database is the one `:core:download` provides (one index for both caches, no module edge).
     */
    @Provides
    @Singleton
    @StreamingCache
    fun streamingCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache =
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(STREAMING_CACHE_BYTES),
            databaseProvider,
        )

    @Provides
    @DownloadSource
    fun downloadSource(
        okHttpClient: OkHttpClient,
        resolver: StreamResolver,
    ): DataSource.Factory = downloadDataSourceFactory(okHttpClient, resolver)
}
