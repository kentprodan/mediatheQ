package io.github.kentprodan.mediatheq.core.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.kentprodan.mediatheq.core.common.ActiveDatabase
import io.github.kentprodan.mediatheq.core.database.dao.DownloadOriginDao
import io.github.kentprodan.mediatheq.core.database.dao.LibraryDao
import io.github.kentprodan.mediatheq.core.database.dao.PlaylistDao
import io.github.kentprodan.mediatheq.core.database.dao.QueueDao
import io.github.kentprodan.mediatheq.core.database.dao.ResumeDao
import io.github.kentprodan.mediatheq.core.database.dao.SyncDao
import io.github.kentprodan.mediatheq.core.database.dao.UserDataDao
import io.github.kentprodan.mediatheq.core.database.dao.VideoDao
import io.github.kentprodan.mediatheq.core.database.dao.WaveformDao
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface AccountDatabasesModule {
    @Binds
    fun accountDatabases(databases: SharedPreferencesAccountDatabases): AccountDatabases
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        databases: AccountDatabases,
    ): MediatheqDatabase =
        Room
            .databaseBuilder(context, MediatheqDatabase::class.java, databases.open)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    /** The active account's database file name, which is also its id (#147). */
    @Provides
    @ActiveDatabase
    fun activeDatabase(databases: AccountDatabases): String = databases.open

    @Provides
    fun libraryDao(database: MediatheqDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun syncDao(database: MediatheqDatabase): SyncDao = database.syncDao()

    @Provides
    fun queueDao(database: MediatheqDatabase): QueueDao = database.queueDao()

    @Provides
    fun playlistDao(database: MediatheqDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun userDataDao(database: MediatheqDatabase): UserDataDao = database.userDataDao()

    @Provides
    fun downloadOriginDao(database: MediatheqDatabase): DownloadOriginDao = database.downloadOriginDao()

    @Provides
    fun waveformDao(database: MediatheqDatabase): WaveformDao = database.waveformDao()

    @Provides
    fun resumeDao(database: MediatheqDatabase): ResumeDao = database.resumeDao()

    @Provides
    fun videoDao(database: MediatheqDatabase): VideoDao = database.videoDao()
}
