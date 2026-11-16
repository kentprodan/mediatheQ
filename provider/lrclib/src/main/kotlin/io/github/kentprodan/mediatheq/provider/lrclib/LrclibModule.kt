package io.github.kentprodan.mediatheq.provider.lrclib

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kentprodan.mediatheq.core.server.OnlineLyricsSource

@Module
@InstallIn(SingletonComponent::class)
internal interface LrclibModule {
    @Binds
    fun onlineLyrics(source: LrclibLyricsSource): OnlineLyricsSource
}
