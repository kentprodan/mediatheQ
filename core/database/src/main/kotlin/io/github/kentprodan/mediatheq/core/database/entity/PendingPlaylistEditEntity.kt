package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * A playlist edit made while the server could not be reached (#417), kept per user in [seq] order and replayed when it
 * is back. [kind] is Add ([trackIds]), Remove ([entryIds]), Move ([entryIds] one entry, to [position]) or Rename ([name]);
 * ids are comma-separated.
 *
 * Every row of a playlist carries its base: the playlist as the server last had it to the phone's knowledge
 * ([baseName], [baseTrackIds]), so the replay can tell whether the server changed meanwhile (#417 part 2).
 * [queuedAt] is when the edit was made (epoch ms).
 */
@Entity(tableName = "pending_playlist_edit")
data class PendingPlaylistEditEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    val kind: String,
    @ColumnInfo(name = "track_ids") val trackIds: String? = null,
    @ColumnInfo(name = "entry_ids") val entryIds: String? = null,
    val position: Int? = null,
    val name: String? = null,
    @ColumnInfo(name = "base_name") val baseName: String? = null,
    @ColumnInfo(name = "base_track_ids") val baseTrackIds: String? = null,
    @ColumnInfo(name = "queued_at") val queuedAt: Long? = null,
)
