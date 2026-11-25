package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity

/**
 * How much earlier (negative) or later a movie's or episode's subtitles show (#456). Apart from the track choice, so a
 * delay on one episode never hides its season's choice. No foreign key; cleared on sign-out.
 */
@Entity(tableName = "video_subtitle_delay", primaryKeys = ["server_id", "item_id"])
data class VideoSubtitleDelayEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "delay_ms") val delayMs: Long,
)
