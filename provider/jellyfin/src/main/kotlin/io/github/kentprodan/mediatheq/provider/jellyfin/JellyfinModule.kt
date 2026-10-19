package io.github.kentprodan.mediatheq.provider.jellyfin

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kentprodan.mediatheq.core.server.AuthSource

@Module
@InstallIn(SingletonComponent::class)
internal interface JellyfinModule {
    @Binds
    fun authSource(source: JellyfinAuthSource): AuthSource
}
