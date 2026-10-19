package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.util.UnstableApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
internal interface PlaybackModule {
    @Binds
    fun playbackConnection(connection: MediaControllerPlaybackConnection): PlaybackConnection

    @Binds
    fun audioDecoders(decoders: MediaCodecAudioDecoders): AudioDecoders
}
