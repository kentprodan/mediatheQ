package io.github.kentprodan.mediatheq.core.download

import android.content.Context
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
import io.github.kentprodan.mediatheq.core.common.DownloadSource
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Singleton

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
internal interface DownloadModule {
    @Binds
    fun downloadRepository(repository: DefaultDownloadRepository): DownloadRepository

    companion object {
        /** Wi-Fi only (any unmetered network); the setting to allow mobile data comes with Settings (#25). */
        val REQUIREMENTS = Requirements(Requirements.NETWORK_UNMETERED)

        private const val MAX_PARALLEL_DOWNLOADS = 2

        /** Media3's own SQLite database for the download index (and later the streaming cache, #21). */
        @Provides
        @Singleton
        fun databaseProvider(
            @ApplicationContext context: Context,
        ): DatabaseProvider = StandaloneDatabaseProvider(context)

        /** In files, not cache: the system never clears it, and nothing is evicted (media3 skill, section 10). */
        @Provides
        @Singleton
        @DownloadCache
        fun downloadCache(
            @ApplicationContext context: Context,
            databaseProvider: DatabaseProvider,
        ): Cache = SimpleCache(File(context.filesDir, "downloads"), NoOpCacheEvictor(), databaseProvider)

        /** Created on the main thread (its callbacks run there); the service and the repository share it. */
        @Provides
        @Singleton
        fun downloadManager(
            @ApplicationContext context: Context,
            databaseProvider: DatabaseProvider,
            @DownloadCache cache: Cache,
            @DownloadSource source: DataSource.Factory,
        ): DownloadManager =
            DownloadManager(
                context,
                databaseProvider,
                cache,
                source,
                Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS),
            ).apply {
                maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
                requirements = REQUIREMENTS
            }
    }
}
