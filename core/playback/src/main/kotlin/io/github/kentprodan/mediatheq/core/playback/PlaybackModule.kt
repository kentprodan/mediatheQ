package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kentprodan.mediatheq.core.common.DownloadSource
import okhttp3.OkHttpClient

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
internal object DownloadSourceModule {
    @Provides
    @DownloadSource
    fun downloadSource(
        okHttpClient: OkHttpClient,
        resolver: StreamResolver,
    ): DataSource.Factory = downloadDataSourceFactory(okHttpClient, resolver)
}
