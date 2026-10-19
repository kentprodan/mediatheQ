package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistEntryStaging
import io.github.kentprodan.mediatheq.core.database.entity.PlaylistStaging
import io.github.kentprodan.mediatheq.core.database.entity.SyncStateEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging

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
    suspend fun stagePlaylists(rows: List<PlaylistStaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stagePlaylistEntries(rows: List<PlaylistEntryStaging>)

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

    /** Replaces the live library with the staged one and records the finished sync. Safe to run twice. */
    @Transaction
    suspend fun swap(finishedAtMillis: Long) {
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
        replacePlaylists()
        copyPlaylists()
        replacePlaylistEntries()
        copyPlaylistEntries()
        clearSearch()
        indexSearch()
        clearStaging()
        saveState(SyncStateEntity(lastFullSyncAtMillis = finishedAtMillis))
    }

    /** Drops a sync in progress (staging and checkpoint) but keeps the live library. */
    @Transaction
    suspend fun discardStaging() {
        clearStaging()
        saveState(SyncStateEntity(lastFullSyncAtMillis = state()?.lastFullSyncAtMillis))
    }

    @Transaction
    suspend fun clearStaging() {
        clearArtistStaging()
        clearAlbumStaging()
        clearAlbumArtistStaging()
        clearTrackStaging()
        clearTrackArtistStaging()
        clearPlaylistStaging()
        clearPlaylistEntryStaging()
    }

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

    @Query("DELETE FROM playlist")
    suspend fun replacePlaylists()

    @Query("INSERT INTO playlist SELECT * FROM playlist_staging")
    suspend fun copyPlaylists()

    @Query("DELETE FROM playlist_entry")
    suspend fun replacePlaylistEntries()

    @Query("INSERT INTO playlist_entry SELECT * FROM playlist_entry_staging")
    suspend fun copyPlaylistEntries()

    @Query("DELETE FROM search")
    suspend fun clearSearch()

    @Query(
        """
        INSERT INTO search (kind, server_id, item_id, name)
        SELECT 'artist', server_id, id, name FROM artist WHERE is_album_artist
        UNION ALL SELECT 'album', server_id, id, title FROM album
        UNION ALL SELECT 'track', server_id, id, title FROM track
        UNION ALL SELECT 'playlist', server_id, id, name FROM playlist
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

    @Query("DELETE FROM playlist_staging")
    suspend fun clearPlaylistStaging()

    @Query("DELETE FROM playlist_entry_staging")
    suspend fun clearPlaylistEntryStaging()
}
