package io.github.kentprodan.mediatheq.core.database

import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * A migration after which the library lacks a new kind of data until the next full sync. A sync in progress is dropped
 * (its staged pages lack it: every table in [staging] is emptied) and the last full sync is dated at the epoch, so the
 * next start syncs at once (it is "over a day old") while the library still counts as synced (screens keep their empty
 * states, not the first-sync one).
 */
abstract class ResyncMigration(
    private val staging: List<String>,
) : AutoMigrationSpec {
    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        staging.forEach { connection.execSQL("DELETE FROM $it") }
        // Never synced stays never synced (NULL); a past sync becomes one at the epoch.
        connection.execSQL(
            "UPDATE sync_state SET checkpoint_server_id = NULL, checkpoint_user_id = NULL, " +
                "checkpoint_started_at = NULL, checkpoint_step = NULL, checkpoint_index = 0, " +
                "last_full_sync_at = CASE WHEN last_full_sync_at IS NULL THEN NULL ELSE 0 END",
        )
    }
}

private val STAGING_6 =
    listOf(
        "artist_staging",
        "album_staging",
        "album_artist_staging",
        "track_staging",
        "track_artist_staging",
        "playlist_staging",
        "playlist_entry_staging",
    )
private val STAGING_7 = STAGING_6 + listOf("album_genre_staging", "track_genre_staging")

/** 6 -> 7 (#130): the library has no genres yet. */
class SyncGenresMigration : ResyncMigration(STAGING_7)

private val STAGING_8 = STAGING_7 + "album_label_staging"

/** 7 -> 8 (#131): the library has no record labels yet. */
class SyncLabelsMigration : ResyncMigration(STAGING_8)

/** 8 -> 9 (#132): albums have no overview or release date, tracks no file size yet. */
class SyncAlbumPageMigration : ResyncMigration(STAGING_8)

/** 9 -> 10 (#133): artists have no backdrop or biography yet. */
class SyncArtistPageMigration : ResyncMigration(STAGING_8)

private val STAGING_12 = STAGING_8 + listOf("series_staging", "season_staging", "video_staging", "video_stream_staging")

/** 11 -> 12 (#270): the library has no videos yet. */
class SyncVideoMigration : ResyncMigration(STAGING_12)
