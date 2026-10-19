package io.github.kentprodan.mediatheq.core.playback

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface PlaybackModule {
    @Binds
    fun playbackConnection(connection: NoOpPlaybackConnection): PlaybackConnection
}
