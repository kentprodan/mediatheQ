package io.github.kentprodan.mediatheq.core.database

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import io.github.kentprodan.mediatheq.core.database.dao.LibraryDao
import io.github.kentprodan.mediatheq.core.database.dao.PlaylistDao
import io.github.kentprodan.mediatheq.core.database.dao.QueueDao
import io.github.kentprodan.mediatheq.core.database.dao.SyncDao
import io.github.kentprodan.mediatheq.core.database.dao.UserDataDao
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistStaging
import io.github.kentprodan.mediatheq.core.database.entity.QueueEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.QueueStateEntity
import io.github.kentprodan.mediatheq.core.database.entity.SearchEntity
import io.github.kentprodan.mediatheq.core.database.entity.SyncStateEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging

/** The local copy of the library (ADR 0006). Schema changes need a migration and a migration test. */
@Database(
    version = 2,
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        AlbumArtistEntity::class,
        TrackEntity::class,
        TrackArtistEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        ArtistStaging::class,
        AlbumStaging::class,
        AlbumArtistStaging::class,
        TrackStaging::class,
        TrackArtistStaging::class,
        PlaylistStaging::class,
        PlaylistEntryStaging::class,
        SearchEntity::class,
        SyncStateEntity::class,
        QueueEntryEntity::class,
        QueueStateEntity::class,
    ],
    autoMigrations = [
        // 2: the saved play queue (#14) and an index on track.album_id.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class MediatheqDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    abstract fun syncDao(): SyncDao

    abstract fun queueDao(): QueueDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun userDataDao(): UserDataDao
}
