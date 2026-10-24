package io.github.kentprodan.mediatheq.provider.jellyfin

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kentprodan.mediatheq.core.server.AuthSource
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import io.github.kentprodan.mediatheq.core.server.PlaybackSource
import io.github.kentprodan.mediatheq.core.server.PlaylistEditor
import io.github.kentprodan.mediatheq.core.server.UserDataSink

@Module
@InstallIn(SingletonComponent::class)
internal interface JellyfinModule {
    @Binds
    fun authSource(source: JellyfinAuthSource): AuthSource

    @Binds
    fun librarySource(source: JellyfinLibrarySource): LibrarySource

    @Binds
    fun playbackSource(source: JellyfinPlaybackSource): PlaybackSource

    @Binds
    fun playlistEditor(editor: JellyfinPlaylistEditor): PlaylistEditor

    @Binds
    fun userDataSink(sink: JellyfinUserDataSink): UserDataSink
}
