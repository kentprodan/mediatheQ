package io.github.kentprodan.mediatheq.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun sessionRepository(repository: DefaultSessionRepository): SessionRepository

    @Binds
    fun librarySyncRepository(repository: DefaultLibrarySyncRepository): LibrarySyncRepository

    @Binds
    fun libraryRepository(repository: DefaultLibraryRepository): LibraryRepository

    @Binds
    fun playbackRepository(repository: DefaultPlaybackRepository): PlaybackRepository

    @Binds
    fun libraryPreferencesRepository(repository: DefaultLibraryPreferencesRepository): LibraryPreferencesRepository
}
