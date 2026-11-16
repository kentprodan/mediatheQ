package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity

/**
 * A series the user follows (#383): its next episodes stay downloaded. No foreign key: a full sync replaces the series
 * and this outlives it. Cleared on sign-out.
 */
@Entity(tableName = "followed_series", primaryKeys = ["server_id", "series_id"])
data class FollowedSeriesEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "series_id") val seriesId: String,
)
