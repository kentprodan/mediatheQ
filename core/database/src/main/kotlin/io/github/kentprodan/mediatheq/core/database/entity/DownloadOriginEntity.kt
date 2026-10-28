package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity

/**
 * What a downloaded track was downloaded through (#168): an album, a playlist, or the track alone ([kind] `Single`,
 * [collectionId] empty). A track downloaded through two collections has two rows. No foreign key to `track`: a full
 * sync replaces the tracks, and downloads (with their origins) outlive it.
 */
@Entity(tableName = "download_origin", primaryKeys = ["server_id", "track_id", "kind", "collection_id"])
data class DownloadOriginEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    val kind: String,
    @ColumnInfo(name = "collection_id") val collectionId: String,
)
