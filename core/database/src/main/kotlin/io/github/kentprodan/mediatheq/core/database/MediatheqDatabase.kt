package io.github.kentprodan.mediatheq.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import io.github.kentprodan.mediatheq.core.database.dao.LibraryDao
import io.github.kentprodan.mediatheq.core.database.dao.SyncDao
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
import io.github.kentprodan.mediatheq.core.database.entity.SearchEntity
import io.github.kentprodan.mediatheq.core.database.entity.SyncStateEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging

/** The local copy of the library (ADR 0006). Schema changes need a migration and a migration test. */
@Database(
    version = 1,
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
    ],
)
abstract class MediatheqDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    abstract fun syncDao(): SyncDao
}
