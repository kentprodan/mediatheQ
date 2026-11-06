package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity

/**
 * Where listening to a playlist stopped (#135), like [AlbumResumeEntity]: recorded only for tracks played from the
 * playlist's page (their queue entries carry its id). No foreign keys, so a full sync keeps it; cleared on sign-out.
 */
@Entity(tableName = "playlist_resume", primaryKeys = ["server_id", "playlist_id"])
data class PlaylistResumeEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "position_ms") val positionMillis: Long,
)
