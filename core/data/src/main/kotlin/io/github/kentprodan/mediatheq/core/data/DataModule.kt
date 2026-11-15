package io.github.kentprodan.mediatheq.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun downloadOrigins(origins: DefaultDownloadOrigins): DownloadOrigins

    @Binds
    fun waveformRepository(repository: DefaultWaveformRepository): WaveformRepository

    @Binds
    fun resumeRepository(repository: DefaultResumeRepository): ResumeRepository

    @Binds
    fun accountRepository(repository: DefaultAccountRepository): AccountRepository

    @Binds
    fun backupRepository(repository: DefaultBackupRepository): BackupRepository

    @Binds
    fun appRestarter(restarter: DefaultAppRestarter): AppRestarter

    @Binds
    fun sessionRepository(repository: DefaultSessionRepository): SessionRepository

    @Binds
    fun librarySyncRepository(repository: DefaultLibrarySyncRepository): LibrarySyncRepository

    @Binds
    fun libraryRepository(repository: DefaultLibraryRepository): LibraryRepository

    @Binds
    fun appModeRepository(repository: DefaultAppModeRepository): AppModeRepository

    @Binds
    fun trackChoiceRepository(repository: DefaultTrackChoiceRepository): TrackChoiceRepository

    @Binds
    fun videoRepository(repository: DefaultVideoRepository): VideoRepository

    @Binds
    fun playbackRepository(repository: DefaultPlaybackRepository): PlaybackRepository

    @Binds
    fun queueRepository(repository: DefaultQueueRepository): QueueRepository

    @Binds
    fun playlistRepository(repository: DefaultPlaylistRepository): PlaylistRepository

    @Binds
    fun lyricsRepository(repository: DefaultLyricsRepository): LyricsRepository

    @Binds
    fun userDataRepository(repository: DefaultUserDataRepository): UserDataRepository

    @Binds
    fun libraryPreferencesRepository(repository: DefaultLibraryPreferencesRepository): LibraryPreferencesRepository

    @Binds
    fun audioPreferencesRepository(repository: DefaultAudioPreferencesRepository): AudioPreferencesRepository

    @Binds
    fun videoPreferencesRepository(repository: DefaultVideoPreferencesRepository): VideoPreferencesRepository

    @Binds
    fun audioOutputRepository(repository: DefaultAudioOutputRepository): AudioOutputRepository
}
