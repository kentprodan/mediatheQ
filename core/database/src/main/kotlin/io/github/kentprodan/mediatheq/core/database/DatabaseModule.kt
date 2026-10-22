package io.github.kentprodan.mediatheq.core.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.kentprodan.mediatheq.core.database.dao.LibraryDao
import io.github.kentprodan.mediatheq.core.database.dao.PlaylistDao
import io.github.kentprodan.mediatheq.core.database.dao.QueueDao
import io.github.kentprodan.mediatheq.core.database.dao.SyncDao
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): MediatheqDatabase =
        Room
            .databaseBuilder(context, MediatheqDatabase::class.java, "mediatheq.db")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    @Provides
    fun libraryDao(database: MediatheqDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun syncDao(database: MediatheqDatabase): SyncDao = database.syncDao()

    @Provides
    fun queueDao(database: MediatheqDatabase): QueueDao = database.queueDao()

    @Provides
    fun playlistDao(database: MediatheqDatabase): PlaylistDao = database.playlistDao()
}
