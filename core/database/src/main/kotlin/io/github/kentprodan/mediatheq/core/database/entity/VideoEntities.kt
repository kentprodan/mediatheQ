package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index

// The video tables (ADR 0048), synced like the music ones: each has a staging twin with the same columns.

/** Video-only user data: where a video was left and the unwatched counts of series and seasons (server data). */
data class VideoUserColumns(
    @ColumnInfo(name = "position_ms") val positionMillis: Long = 0,
    @ColumnInfo(name = "played_percentage") val playedPercentage: Double? = null,
    @ColumnInfo(name = "unplayed_count") val unplayedCount: Int? = null,
)

@Entity(tableName = "series", primaryKeys = ["server_id", "id"])
data class SeriesEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    val id: String,
    val title: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    val overview: String? = null,
    val year: Int? = null,
    @ColumnInfo(name = "date_added") val dateAddedMillis: Long? = null,
    /** Genre names, one per line (a video genre page is later, ADR 0048). */
    val genres: String? = null,
    @Embedded val image: ImageColumns = ImageColumns(),
    @Embedded(prefix = "backdrop_") val backdrop: ImageColumns = ImageColumns(),
    @Embedded val userData: UserDataColumns = UserDataColumns(),
    @Embedded val videoUser: VideoUserColumns = VideoUserColumns(),
)

@Entity(tableName = "season", primaryKeys = ["server_id", "id"], indices = [Index("server_id", "series_id")])
data class SeasonEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    val id: String,
    @ColumnInfo(name = "series_id") val seriesId: String,
    val title: String,
    val number: Int? = null,
    @Embedded val image: ImageColumns = ImageColumns(),
    @Embedded val userData: UserDataColumns = UserDataColumns(),
    @Embedded val videoUser: VideoUserColumns = VideoUserColumns(),
)

/** Movies and episodes (`kind` is the `VideoKind` name); the one video stream's format is folded in. */
@Entity(
    tableName = "video",
    primaryKeys = ["server_id", "id"],
    indices = [Index("server_id", "series_id"), Index("server_id", "season_id")],
)
data class VideoEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    val id: String,
    val kind: String,
    val title: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    val overview: String? = null,
    val year: Int? = null,
    @ColumnInfo(name = "premiere_date") val premiereDateMillis: Long? = null,
    @ColumnInfo(name = "date_added") val dateAddedMillis: Long? = null,
    @ColumnInfo(name = "duration_ms") val durationMillis: Long = 0,
    @ColumnInfo(name = "series_id") val seriesId: String? = null,
    @ColumnInfo(name = "series_name") val seriesName: String? = null,
    @ColumnInfo(name = "season_id") val seasonId: String? = null,
    @ColumnInfo(name = "season_number") val seasonNumber: Int? = null,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int? = null,
    val genres: String? = null,
    val tagline: String? = null,
    @ColumnInfo(name = "official_rating") val officialRating: String? = null,
    @ColumnInfo(name = "video_codec") val videoCodec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** `VideoRange` name. */
    @ColumnInfo(name = "video_range") val videoRange: String? = null,
    @ColumnInfo(name = "bitrate_kbps") val bitrateKbps: Int? = null,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long? = null,
    @Embedded val image: ImageColumns = ImageColumns(),
    @Embedded(prefix = "backdrop_") val backdrop: ImageColumns = ImageColumns(),
    @Embedded val userData: UserDataColumns = UserDataColumns(),
    @Embedded val videoUser: VideoUserColumns = VideoUserColumns(),
)

/** An audio or subtitle stream of a video (`type` is the `StreamType` name). */
@Entity(tableName = "video_stream", primaryKeys = ["server_id", "video_id", "stream_index"])
data class VideoStreamEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "stream_index") val streamIndex: Int,
    val type: String,
    val codec: String? = null,
    val language: String? = null,
    val title: String? = null,
    @ColumnInfo(name = "display_title") val displayTitle: String? = null,
    val channels: Int? = null,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "is_forced") val isForced: Boolean = false,
    @ColumnInfo(name = "is_external") val isExternal: Boolean = false,
)

@Entity(tableName = "series_staging", primaryKeys = ["server_id", "id"])
data class SeriesStaging(
    @Embedded val row: SeriesEntity,
)

@Entity(tableName = "season_staging", primaryKeys = ["server_id", "id"])
data class SeasonStaging(
    @Embedded val row: SeasonEntity,
)

@Entity(tableName = "video_staging", primaryKeys = ["server_id", "id"])
data class VideoStaging(
    @Embedded val row: VideoEntity,
)

@Entity(tableName = "video_stream_staging", primaryKeys = ["server_id", "video_id", "stream_index"])
data class VideoStreamStaging(
    @Embedded val row: VideoStreamEntity,
)
