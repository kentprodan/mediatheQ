package io.github.kentprodan.mediatheq.core.database

import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * 6 -> 7 (#130): the library has no genres yet. A sync in progress is dropped (its staged pages carry none) and the last
 * full sync is dated at the epoch, so the next start syncs at once (it is "over a day old") while the library still
 * counts as synced (screens keep their empty states, not the first-sync one).
 */
class SyncGenresMigration : AutoMigrationSpec {
    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        listOf(
            "artist_staging",
            "album_staging",
            "album_artist_staging",
            "track_staging",
            "track_artist_staging",
            "playlist_staging",
            "playlist_entry_staging",
        ).forEach { connection.execSQL("DELETE FROM $it") }
        // Never synced stays never synced (NULL); a past sync becomes one at the epoch.
        connection.execSQL(
            "UPDATE sync_state SET checkpoint_server_id = NULL, checkpoint_user_id = NULL, " +
                "checkpoint_started_at = NULL, checkpoint_step = NULL, checkpoint_index = 0, " +
                "last_full_sync_at = CASE WHEN last_full_sync_at IS NULL THEN NULL ELSE 0 END",
        )
    }
}
