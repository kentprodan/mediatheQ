package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity

/**
 * The audio and subtitle tracks chosen for a movie, an episode or a whole season (ADR 0050, #274); [itemId] is any of
 * them. No foreign key: a full sync replaces the videos and this outlives it. Cleared on sign-out.
 */
@Entity(tableName = "video_track_choice", primaryKeys = ["server_id", "item_id"])
data class VideoTrackChoiceEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "audio_index") val audioIndex: Int? = null,
    @ColumnInfo(name = "audio_language") val audioLanguage: String? = null,
    @ColumnInfo(name = "subtitle_index") val subtitleIndex: Int? = null,
    @ColumnInfo(name = "subtitle_language") val subtitleLanguage: String? = null,
    @ColumnInfo(name = "subtitle_off") val subtitleOff: Boolean = false,
)
