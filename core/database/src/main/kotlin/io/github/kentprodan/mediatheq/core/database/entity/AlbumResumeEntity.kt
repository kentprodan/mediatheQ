package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity

/**
 * Where listening to an album stopped (#132): the track and the position in it, kept by the app because the server
 * keeps no positions for music (jellyfin R20). No foreign key to `album` or `track`: a full sync replaces them, and
 * this outlives it. Cleared on sign-out.
 */
@Entity(tableName = "album_resume", primaryKeys = ["server_id", "album_id"])
data class AlbumResumeEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "album_id") val albumId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "position_ms") val positionMillis: Long,
)
