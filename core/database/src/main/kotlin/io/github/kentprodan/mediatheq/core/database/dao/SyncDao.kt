package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumGenreStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumLabelStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistStaging
import io.github.kentprodan.mediatheq.core.database.entity.SeasonStaging
import io.github.kentprodan.mediatheq.core.database.entity.SeriesStaging
import io.github.kentprodan.mediatheq.core.database.entity.SyncStateEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackGenreStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.database.entity.VideoStaging
import io.github.kentprodan.mediatheq.core.database.entity.VideoStreamStaging

/**
 * Writes of a full sync: pages go to the staging tables together with the checkpoint, and [swap] replaces the live
 * library with them in one transaction, so readers see either the old or the new library, never a mix.
 * Writes use REPLACE, not @Upsert: the tables have no foreign keys, and Room's upsert relies on Android's
 * SQLiteConstraintException, which the bundled driver in JVM tests does not throw.
 */
@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE id = 0")
    suspend fun state(): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: SyncStateEntity)

    /** Album artists win over the name-only rows track credits create. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageAlbumArtists(rows: List<ArtistStaging>)

    /** Track-only artists: added only when the artist is not staged yet. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun stageCreditedArtists(rows: List<ArtistStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageAlbums(rows: List<AlbumStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageAlbumArtistLinks(rows: List<AlbumArtistStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageTracks(rows: List<TrackStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageTrackArtistLinks(rows: List<TrackArtistStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageAlbumGenreLinks(rows: List<AlbumGenreStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageTrackGenreLinks(rows: List<TrackGenreStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageAlbumLabelLinks(rows: List<AlbumLabelStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stagePlaylists(rows: List<PlaylistStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stagePlaylistEntries(rows: List<PlaylistEntryStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageVideos(rows: List<VideoStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageVideoStreams(rows: List<VideoStreamStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageSeries(rows: List<SeriesStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageSeasons(rows: List<SeasonStaging>)

    @Query("SELECT id FROM playlist_staging ORDER BY sort_name, id")
    suspend fun stagedPlaylistIds(): List<String>

    /** Stores one fetched page and the position to continue from, atomically. */
    @Transaction
    suspend fun stagePage(
        checkpoint: SyncStateEntity,
        write: suspend SyncDao.() -> Unit,
    ) {
        write()
        saveState(checkpoint)
    }

    /** Replaces the live library with the staged one and records the finished sync and its owner. */
    @Transaction
    suspend fun swap(
        finishedAtMillis: Long,
        ownerServerId: String,
        ownerUserId: String,
    ) {
        mergeSameNameArtists()
        replaceArtists()
        copyArtists()
        replaceAlbums()
        copyAlbums()
        replaceAlbumArtists()
        copyAlbumArtists()
        replaceTracks()
        copyTracks()
        replaceTrackArtists()
        copyTrackArtists()
        replaceAlbumGenres()
        copyAlbumGenres()
        replaceAlbumLabels()
        copyAlbumLabels()
        replaceTrackGenres()
        copyTrackGenres()
        replacePlaylists()
        copyPlaylists()
        replacePlaylistEntries()
        copyPlaylistEntries()
        replaceVideoLibrary()
        copyVideoLibrary()
        clearSearch()
        indexSearch()
        clearStaging()
        saveState(SyncStateEntity(0, finishedAtMillis, ownerServerId, ownerUserId))
    }

    /**
     * R16 (jellyfin skill): an artist with folders in two libraries is two server items, and albums and tracks may credit
     * the one the album artist list leaves out. Credits of such a credit-only artist move to its one album artist
     * namesake (the server ignores case; shortcut: NOCASE folds ASCII only, use a Unicode fold if an
     * accented name ever shows twice), a credit that is then doubled is dropped, and a credit-only
     * artist still credited by an album becomes an album artist.
     */
    @Transaction
    suspend fun mergeSameNameArtists() {
        moveAlbumCreditsToNamesake()
        moveTrackCreditsToNamesake()
        dropDoubledAlbumCredits()
        dropDoubledTrackCredits()
        markCreditedAlbumArtists()
    }

    @Query(
        """
        UPDATE album_artist_staging SET artist_id = COALESCE((
            SELECT MIN(a.id) FROM artist_staging c
            JOIN artist_staging a ON a.server_id = c.server_id AND a.is_album_artist AND a.name = c.name COLLATE NOCASE
            WHERE c.server_id = album_artist_staging.server_id AND c.id = album_artist_staging.artist_id
            AND NOT c.is_album_artist
            GROUP BY c.id HAVING COUNT(*) = 1
        ), artist_id)
        """,
    )
    suspend fun moveAlbumCreditsToNamesake()

    @Query(
        """
        UPDATE track_artist_staging SET artist_id = COALESCE((
            SELECT MIN(a.id) FROM artist_staging c
            JOIN artist_staging a ON a.server_id = c.server_id AND a.is_album_artist AND a.name = c.name COLLATE NOCASE
            WHERE c.server_id = track_artist_staging.server_id AND c.id = track_artist_staging.artist_id
            AND NOT c.is_album_artist
            GROUP BY c.id HAVING COUNT(*) = 1
        ), artist_id)
        """,
    )
    suspend fun moveTrackCreditsToNamesake()

    @Query(
        """
        DELETE FROM album_artist_staging WHERE EXISTS (
            SELECT 1 FROM album_artist_staging e
            WHERE e.server_id = album_artist_staging.server_id AND e.album_id = album_artist_staging.album_id
            AND e.artist_id = album_artist_staging.artist_id AND e.position < album_artist_staging.position
        )
        """,
    )
    suspend fun dropDoubledAlbumCredits()

    @Query(
        """
        DELETE FROM track_artist_staging WHERE EXISTS (
            SELECT 1 FROM track_artist_staging e
            WHERE e.server_id = track_artist_staging.server_id AND e.track_id = track_artist_staging.track_id
            AND e.artist_id = track_artist_staging.artist_id AND e.position < track_artist_staging.position
        )
        """,
    )
    suspend fun dropDoubledTrackCredits()

    @Query(
        """
        UPDATE artist_staging SET is_album_artist = 1 WHERE NOT is_album_artist AND EXISTS (
            SELECT 1 FROM album_artist_staging l
            WHERE l.server_id = artist_staging.server_id AND l.artist_id = artist_staging.id
        )
        """,
    )
    suspend fun markCreditedAlbumArtists()

    /** Drops a sync in progress (staging and checkpoint) but keeps the live library. */
    @Transaction
    suspend fun discardStaging() {
        clearStaging()
        val state = state()
        saveState(SyncStateEntity(0, state?.lastFullSyncAtMillis, state?.ownerServerId, state?.ownerUserId))
    }

    /**
     * Forgets the whole library: live tables, staging, search index, sync state, the saved queue and unsent favorite or
     * rating changes and the waveforms (sign-out).
     */
    @Transaction
    suspend fun clearAll() {
        clearPendingUserData()
        clearStaging()
        replaceArtists()
        replaceAlbums()
        replaceAlbumArtists()
        replaceTracks()
        replaceTrackArtists()
        replaceAlbumGenres()
        replaceTrackGenres()
        replaceAlbumLabels()
        replacePlaylists()
        replacePlaylistEntries()
        replaceVideoLibrary()
        clearSearch()
        clearState()
        clearAlbumResume()
        clearPlaylistResume()
        clearQueueEntries()
        clearQueueState()
        clearWaveforms()
    }

    @Query("DELETE FROM waveform")
    suspend fun clearWaveforms()

    @Query("DELETE FROM album_resume")
    suspend fun clearAlbumResume()

    @Query("DELETE FROM playlist_resume")
    suspend fun clearPlaylistResume()

    @Query("DELETE FROM pending_user_data")
    suspend fun clearPendingUserData()

    @Query("DELETE FROM queue_entry")
    suspend fun clearQueueEntries()

    @Query("DELETE FROM queue_state")
    suspend fun clearQueueState()

    @Query("DELETE FROM sync_state")
    suspend fun clearState()

    @Transaction
    suspend fun clearStaging() {
        clearArtistStaging()
        clearAlbumStaging()
        clearAlbumArtistStaging()
        clearTrackStaging()
        clearTrackArtistStaging()
        clearAlbumGenreStaging()
        clearTrackGenreStaging()
        clearAlbumLabelStaging()
        clearPlaylistStaging()
        clearPlaylistEntryStaging()
        clearVideoStaging()
        clearVideoStreamStaging()
        clearSeriesStaging()
        clearSeasonStaging()
    }

    @Transaction
    suspend fun replaceVideoLibrary() {
        replaceVideos()
        replaceVideoStreams()
        replaceSeries()
        replaceSeasons()
    }

    @Transaction
    suspend fun copyVideoLibrary() {
        copyVideos()
        copyVideoStreams()
        copySeries()
        copySeasons()
    }

    @Query("DELETE FROM video")
    suspend fun replaceVideos()

    @Query("INSERT INTO video SELECT * FROM video_staging")
    suspend fun copyVideos()

    @Query("DELETE FROM video_stream")
    suspend fun replaceVideoStreams()

    @Query("INSERT INTO video_stream SELECT * FROM video_stream_staging")
    suspend fun copyVideoStreams()

    @Query("DELETE FROM series")
    suspend fun replaceSeries()

    @Query("INSERT INTO series SELECT * FROM series_staging")
    suspend fun copySeries()

    @Query("DELETE FROM season")
    suspend fun replaceSeasons()

    @Query("INSERT INTO season SELECT * FROM season_staging")
    suspend fun copySeasons()

    @Query("DELETE FROM video_staging")
    suspend fun clearVideoStaging()

    @Query("DELETE FROM video_staging WHERE kind = :kind")
    suspend fun clearVideoStaging(kind: String)

    @Query("DELETE FROM video_stream_staging")
    suspend fun clearVideoStreamStaging()

    @Query(
        "DELETE FROM video_stream_staging WHERE NOT EXISTS (SELECT 1 FROM video_staging v " +
            "WHERE v.server_id = video_stream_staging.server_id AND v.id = video_stream_staging.video_id)",
    )
    suspend fun clearOrphanVideoStreamStaging()

    @Query("DELETE FROM series_staging")
    suspend fun clearSeriesStaging()

    @Query("DELETE FROM season_staging")
    suspend fun clearSeasonStaging()

    @Query("SELECT COUNT(*) FROM video_staging WHERE kind = :kind")
    suspend fun stagedVideoCount(kind: String): Int

    @Query("SELECT COUNT(*) FROM series_staging")
    suspend fun stagedSeriesCount(): Int

    @Query("SELECT COUNT(*) FROM season_staging")
    suspend fun stagedSeasonCount(): Int

    @Query("DELETE FROM artist")
    suspend fun replaceArtists()

    @Query("INSERT INTO artist SELECT * FROM artist_staging")
    suspend fun copyArtists()

    @Query("DELETE FROM album")
    suspend fun replaceAlbums()

    @Query("INSERT INTO album SELECT * FROM album_staging")
    suspend fun copyAlbums()

    @Query("DELETE FROM album_artist")
    suspend fun replaceAlbumArtists()

    @Query("INSERT INTO album_artist SELECT * FROM album_artist_staging")
    suspend fun copyAlbumArtists()

    @Query("DELETE FROM track")
    suspend fun replaceTracks()

    @Query("INSERT INTO track SELECT * FROM track_staging")
    suspend fun copyTracks()

    @Query("DELETE FROM track_artist")
    suspend fun replaceTrackArtists()

    @Query("INSERT INTO track_artist SELECT * FROM track_artist_staging")
    suspend fun copyTrackArtists()

    @Query("DELETE FROM album_genre")
    suspend fun replaceAlbumGenres()

    @Query("INSERT INTO album_genre SELECT * FROM album_genre_staging")
    suspend fun copyAlbumGenres()

    @Query("DELETE FROM album_label")
    suspend fun replaceAlbumLabels()

    @Query("INSERT INTO album_label SELECT * FROM album_label_staging")
    suspend fun copyAlbumLabels()

    @Query("DELETE FROM track_genre")
    suspend fun replaceTrackGenres()

    @Query("INSERT INTO track_genre SELECT * FROM track_genre_staging")
    suspend fun copyTrackGenres()

    @Query("DELETE FROM playlist")
    suspend fun replacePlaylists()

    @Query("INSERT INTO playlist SELECT * FROM playlist_staging")
    suspend fun copyPlaylists()

    @Query("DELETE FROM playlist_entry")
    suspend fun replacePlaylistEntries()

    @Query("INSERT INTO playlist_entry SELECT * FROM playlist_entry_staging")
    suspend fun copyPlaylistEntries()

    @Query("SELECT COUNT(*) FROM artist_staging WHERE is_album_artist")
    suspend fun stagedAlbumArtistCount(): Int

    @Query("SELECT COUNT(*) FROM album_staging")
    suspend fun stagedAlbumCount(): Int

    @Query("SELECT COUNT(*) FROM track_staging")
    suspend fun stagedTrackCount(): Int

    @Query("SELECT COUNT(*) FROM playlist_staging")
    suspend fun stagedPlaylistCount(): Int

    @Query("DELETE FROM search")
    suspend fun clearSearch()

    @Query(
        """
        INSERT INTO search (kind, server_id, item_id, name)
        SELECT 'artist', server_id, id, name FROM artist WHERE is_album_artist
        UNION ALL SELECT 'album', server_id, id, title FROM album
        UNION ALL SELECT 'track', server_id, id, title FROM track
        UNION ALL SELECT 'playlist', server_id, id, name FROM playlist
        UNION ALL SELECT 'genre', server_id, genre_id, MAX(genre_name) FROM (
            SELECT server_id, genre_id, genre_name FROM album_genre
            UNION ALL SELECT server_id, genre_id, genre_name FROM track_genre
        ) GROUP BY server_id, genre_id
        UNION ALL SELECT 'label', server_id, label_id, MAX(label_name) FROM album_label GROUP BY server_id, label_id
        UNION ALL SELECT CASE kind WHEN 'Movie' THEN 'movie' ELSE 'episode' END, server_id, id, title FROM video
        UNION ALL SELECT 'series', server_id, id, title FROM series
        """,
    )
    suspend fun indexSearch()

    @Query("DELETE FROM artist_staging")
    suspend fun clearArtistStaging()

    @Query("DELETE FROM album_staging")
    suspend fun clearAlbumStaging()

    @Query("DELETE FROM album_artist_staging")
    suspend fun clearAlbumArtistStaging()

    @Query("DELETE FROM track_staging")
    suspend fun clearTrackStaging()

    @Query("DELETE FROM track_artist_staging")
    suspend fun clearTrackArtistStaging()

    @Query("DELETE FROM album_genre_staging")
    suspend fun clearAlbumGenreStaging()

    @Query("DELETE FROM track_genre_staging")
    suspend fun clearTrackGenreStaging()

    @Query("DELETE FROM album_label_staging")
    suspend fun clearAlbumLabelStaging()

    @Query("DELETE FROM playlist_staging")
    suspend fun clearPlaylistStaging()

    @Query("DELETE FROM playlist_entry_staging")
    suspend fun clearPlaylistEntryStaging()
}
