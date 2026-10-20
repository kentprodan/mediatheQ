package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index

// One file for the library tables: each staging table embeds its live entity, so both always share the same columns
// in the same order (the swap copies them with SELECT *).
// shortcut: no foreign keys between synced tables; a dangling reference (e.g. a playlist entry the user cannot see)
// must not abort the whole swap. Reads join and skip what is missing.

/** Per-user state the server keeps for an item. */
data class UserDataColumns(
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    val rating: Double? = null,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "last_played_at") val lastPlayedAtMillis: Long? = null,
    val played: Boolean = false,
)

/** Primary image of an item: [itemId] owns the image (a track may use its album's). */
data class ImageColumns(
    @ColumnInfo(name = "image_item_id") val itemId: String? = null,
    @ColumnInfo(name = "image_tag") val tag: String? = null,
    @ColumnInfo(name = "blur_hash") val blurHash: String? = null,
)

@Entity(tableName = "artist", primaryKeys = ["server_id", "id"])
data class ArtistEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    val id: String,
    val name: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    /** Album artists are listed in the Library; track-only artists exist for credits and search. */
    @ColumnInfo(name = "is_album_artist") val isAlbumArtist: Boolean,
    @Embedded val image: ImageColumns = ImageColumns(),
    @Embedded val userData: UserDataColumns = UserDataColumns(),
)

@Entity(tableName = "album", primaryKeys = ["server_id", "id"])
data class AlbumEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    val id: String,
    val title: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    val year: Int? = null,
    @ColumnInfo(name = "track_count") val trackCount: Int = 0,
    @ColumnInfo(name = "duration_ms") val durationMillis: Long = 0,
    @ColumnInfo(name = "date_added") val dateAddedMillis: Long? = null,
    @ColumnInfo(name = "normalization_gain") val normalizationGainDb: Double? = null,
    @Embedded val image: ImageColumns = ImageColumns(),
    @Embedded val userData: UserDataColumns = UserDataColumns(),
)

/** Album -> artist, in credit order. */
@Entity(tableName = "album_artist", primaryKeys = ["server_id", "album_id", "position"])
data class AlbumArtistEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "album_id") val albumId: String,
    val position: Int,
    @ColumnInfo(name = "artist_id") val artistId: String,
)

@Entity(
    tableName = "track",
    primaryKeys = ["server_id", "id"],
    indices = [Index("server_id", "album_id")],
)
data class TrackEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    val id: String,
    val title: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    @ColumnInfo(name = "album_id") val albumId: String? = null,
    @ColumnInfo(name = "album_title") val albumTitle: String? = null,
    @ColumnInfo(name = "track_number") val trackNumber: Int? = null,
    @ColumnInfo(name = "disc_number") val discNumber: Int? = null,
    @ColumnInfo(name = "duration_ms") val durationMillis: Long = 0,
    @ColumnInfo(name = "date_added") val dateAddedMillis: Long? = null,
    @ColumnInfo(name = "normalization_gain") val normalizationGainDb: Double? = null,
    val codec: String? = null,
    @ColumnInfo(name = "sample_rate") val sampleRateHz: Int? = null,
    @ColumnInfo(name = "bit_depth") val bitDepth: Int? = null,
    val channels: Int? = null,
    @ColumnInfo(name = "bitrate_kbps") val bitrateKbps: Int? = null,
    /** `SpatialFormat` name, or `null` for plain audio. */
    @ColumnInfo(name = "spatial_format") val spatialFormat: String? = null,
    @Embedded val image: ImageColumns = ImageColumns(),
    @Embedded val userData: UserDataColumns = UserDataColumns(),
)

/** Track -> artist, in credit order. */
@Entity(tableName = "track_artist", primaryKeys = ["server_id", "track_id", "position"])
data class TrackArtistEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    val position: Int,
    @ColumnInfo(name = "artist_id") val artistId: String,
)

@Entity(tableName = "playlist", primaryKeys = ["server_id", "id"])
data class PlaylistEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    val id: String,
    val name: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    @ColumnInfo(name = "track_count") val trackCount: Int = 0,
    @ColumnInfo(name = "duration_ms") val durationMillis: Long = 0,
    @ColumnInfo(name = "date_added") val dateAddedMillis: Long? = null,
    @Embedded val image: ImageColumns = ImageColumns(),
    @Embedded val userData: UserDataColumns = UserDataColumns(),
)

/** One position in a playlist. [entryId] is the server's playlist entry id, needed to remove it (#16). */
@Entity(tableName = "playlist_entry", primaryKeys = ["server_id", "playlist_id", "position"])
data class PlaylistEntryEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    val position: Int,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
)

@Entity(tableName = "artist_staging", primaryKeys = ["server_id", "id"])
data class ArtistStaging(
    @Embedded val row: ArtistEntity,
)

@Entity(tableName = "album_staging", primaryKeys = ["server_id", "id"])
data class AlbumStaging(
    @Embedded val row: AlbumEntity,
)

@Entity(tableName = "album_artist_staging", primaryKeys = ["server_id", "album_id", "position"])
data class AlbumArtistStaging(
    @Embedded val row: AlbumArtistEntity,
)

@Entity(tableName = "track_staging", primaryKeys = ["server_id", "id"])
data class TrackStaging(
    @Embedded val row: TrackEntity,
)

@Entity(tableName = "track_artist_staging", primaryKeys = ["server_id", "track_id", "position"])
data class TrackArtistStaging(
    @Embedded val row: TrackArtistEntity,
)

@Entity(tableName = "playlist_staging", primaryKeys = ["server_id", "id"])
data class PlaylistStaging(
    @Embedded val row: PlaylistEntity,
)

@Entity(tableName = "playlist_entry_staging", primaryKeys = ["server_id", "playlist_id", "position"])
data class PlaylistEntryStaging(
    @Embedded val row: PlaylistEntryEntity,
)
