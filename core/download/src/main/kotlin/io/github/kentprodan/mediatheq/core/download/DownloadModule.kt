package io.github.kentprodan.mediatheq.core.download

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.kentprodan.mediatheq.core.common.DownloadCache
import io.github.kentprodan.mediatheq.core.common.DownloadCoverDir
import io.github.kentprodan.mediatheq.core.common.DownloadSource
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.DownloadStorageRepository
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Named
import javax.inject.Singleton

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
internal interface DownloadModule {
    @Binds
    fun downloadRepository(repository: DefaultDownloadRepository): DownloadRepository

    @Binds
    fun downloadStorageRepository(storage: DownloadStorage): DownloadStorageRepository

    @Binds
    fun volumes(volumes: AndroidVolumes): Volumes

    companion object {
        /** Wi-Fi only (any unmetered network), or any network: the Settings choice, applied by the repository. */
        fun requirements(unmeteredOnly: Boolean) =
            Requirements(if (unmeteredOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

        /** Enough threads for the most parallel downloads the setting allows (#144). */
        private val DOWNLOAD_THREADS = AudioPreferences.PARALLEL_DOWNLOADS.last

        /** Media3's own SQLite database for the download index (and later the streaming cache, #21). */
        @Provides
        @Singleton
        fun databaseProvider(
            @ApplicationContext context: Context,
        ): DatabaseProvider = StandaloneDatabaseProvider(context)

        /** The download location, read before the cache opens (#146). */
        @Provides
        @Singleton
        @Named(DownloadStorage.PREFERENCES)
        fun storagePreferences(
            @ApplicationContext context: Context,
        ): SharedPreferences = context.getSharedPreferences(DownloadStorage.PREFERENCES, Context.MODE_PRIVATE)

        /**
         * In files (internal or an SD card's, #146), not cache: the system never clears it, and nothing is evicted
         * (media3 skill, section 10). Guarded: never touched while the card is out.
         */
        @Provides
        @Singleton
        @DownloadCache
        fun downloadCache(
            storage: DownloadStorage,
            databaseProvider: DatabaseProvider,
        ): Cache =
            GuardedCache(storage.root, storage.available::value) {
                SimpleCache(storage.downloadDir, NoOpCacheEvictor(), databaseProvider)
            }

        /** Covers of downloaded albums (#154), in files next to the downloads: never cleared by the system. */
        @Provides
        @DownloadCoverDir
        fun downloadCoverDir(
            @ApplicationContext context: Context,
        ): File = File(context.filesDir, "download_covers")

        /** Created on the main thread (its callbacks run there); the service and the repository share it. */
        @Provides
        @Singleton
        fun downloadManager(
            @ApplicationContext context: Context,
            databaseProvider: DatabaseProvider,
            @DownloadCache cache: Cache,
            @DownloadSource source: DataSource.Factory,
            storage: DownloadStorage,
        ): DownloadManager =
            DownloadManager(
                context,
                databaseProvider,
                cache,
                source,
                Executors.newFixedThreadPool(DOWNLOAD_THREADS),
            ).apply {
                maxParallelDownloads = AudioPreferences.DEFAULT_PARALLEL_DOWNLOADS
                requirements = requirements(unmeteredOnly = true)
                // Before the stored downloads start: with the card out they would only fail (#146).
                if (!storage.available.value) pauseDownloads()
            }
    }
}
