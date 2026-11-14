package io.github.kentprodan.mediatheq.core.database

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import io.github.kentprodan.mediatheq.core.database.dao.DownloadOriginDao
import io.github.kentprodan.mediatheq.core.database.dao.LibraryDao
import io.github.kentprodan.mediatheq.core.database.dao.PlaylistDao
import io.github.kentprodan.mediatheq.core.database.dao.QueueDao
import io.github.kentprodan.mediatheq.core.database.dao.ResumeDao
import io.github.kentprodan.mediatheq.core.database.dao.SyncDao
import io.github.kentprodan.mediatheq.core.database.dao.UserDataDao
import io.github.kentprodan.mediatheq.core.database.dao.VideoDao
import io.github.kentprodan.mediatheq.core.database.dao.WaveformDao
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumGenreEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumGenreStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumLabelEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumLabelStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumResumeEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.DownloadOriginEntity
import io.github.kentprodan.mediatheq.core.database.entity.PendingUserDataEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistResumeEntity
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistStaging
import io.github.kentprodan.mediatheq.core.database.entity.QueueEntryEntity
import io.github.kentprodan.mediatheq.core.database.entity.QueueStateEntity
import io.github.kentprodan.mediatheq.core.database.entity.SearchEntity
import io.github.kentprodan.mediatheq.core.database.entity.SeasonEntity
import io.github.kentprodan.mediatheq.core.database.entity.SeasonStaging
import io.github.kentprodan.mediatheq.core.database.entity.SeriesEntity
import io.github.kentprodan.mediatheq.core.database.entity.SeriesStaging
import io.github.kentprodan.mediatheq.core.database.entity.SyncStateEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackGenreEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackGenreStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.database.entity.VideoEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoStaging
import io.github.kentprodan.mediatheq.core.database.entity.VideoStreamEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoStreamStaging
import io.github.kentprodan.mediatheq.core.database.entity.VideoTrackChoiceEntity
import io.github.kentprodan.mediatheq.core.database.entity.WaveformEntity

/** The local copy of the library (ADR 0006). Schema changes need a migration and a migration test. */
@Database(
    version = 14,
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
        PendingUserDataEntity::class,
        DownloadOriginEntity::class,
        WaveformEntity::class,
        AlbumGenreEntity::class,
        TrackGenreEntity::class,
        AlbumGenreStaging::class,
        TrackGenreStaging::class,
        AlbumLabelEntity::class,
        AlbumLabelStaging::class,
        AlbumResumeEntity::class,
        VideoTrackChoiceEntity::class,
        PlaylistResumeEntity::class,
        SeriesEntity::class,
        SeasonEntity::class,
        VideoEntity::class,
        VideoStreamEntity::class,
        SeriesStaging::class,
        SeasonStaging::class,
        VideoStaging::class,
        VideoStreamStaging::class,
    ],
    autoMigrations = [
        // 2: the saved play queue (#14) and an index on track.album_id.
        AutoMigration(from = 1, to = 2),
        // 3: the saved repeat mode (#41).
        AutoMigration(from = 2, to = 3),
        // 4: favorite and rating changes waiting for the server (#56).
        AutoMigration(from = 3, to = 4),
        // 5: what each download was started from (#168).
        AutoMigration(from = 4, to = 5),
        // 6: waveforms computed on the device (#124).
        AutoMigration(from = 5, to = 6),
        // 7: album and track genres (#130); the next start runs a full sync that fills them.
        AutoMigration(from = 6, to = 7, spec = SyncGenresMigration::class),
        // 8: album record labels (#131).
        AutoMigration(from = 7, to = 8, spec = SyncLabelsMigration::class),
        // 9: album overview and release date, track file size, where each album was left (#132).
        AutoMigration(from = 8, to = 9, spec = SyncAlbumPageMigration::class),
        // #133: artists have no backdrop or biography yet.
        AutoMigration(from = 9, to = 10, spec = SyncArtistPageMigration::class),
        // 11: where each playlist was left, and the playlist a queue entry was played from (#135); no resync.
        AutoMigration(from = 10, to = 11),
        // 12: movies, series, seasons, episodes and their streams (#270, ADR 0048); the next start syncs them.
        AutoMigration(from = 11, to = 12, spec = SyncVideoMigration::class),
        // 13: a watched change waiting for the server, beside favorite and rating (#273); no resync.
        AutoMigration(from = 12, to = 13),
        // 14: the audio and subtitle tracks chosen per movie, episode or season (#274); no resync.
        AutoMigration(from = 13, to = 14),
    ],
)
abstract class MediatheqDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    abstract fun syncDao(): SyncDao

    abstract fun queueDao(): QueueDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun userDataDao(): UserDataDao

    abstract fun downloadOriginDao(): DownloadOriginDao

    abstract fun waveformDao(): WaveformDao

    abstract fun resumeDao(): ResumeDao

    abstract fun videoDao(): VideoDao
}
