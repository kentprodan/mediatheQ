package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity

/**
 * A track's waveform computed on the device (#124, ADR 0027): one byte (0-255) per bucket, analyzed from the file of
 * [sizeBytes]; another size means another file, analyzed again. `null` [levels] = no decoder on this device for the
 * file's format (E-AC-3 JOC, TrueHD), so it is not tried again. No foreign key to `track`: a full sync replaces the
 * tracks, and a waveform outlives it.
 */
@Entity(tableName = "waveform", primaryKeys = ["server_id", "item_id"])
data class WaveformEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val levels: ByteArray?,
) {
    // A ByteArray compares by reference; rows compare by content.
    override fun equals(other: Any?): Boolean =
        other is WaveformEntity &&
            serverId == other.serverId &&
            itemId == other.itemId &&
            sizeBytes == other.sizeBytes &&
            levels.contentEquals(other.levels)

    override fun hashCode(): Int =
        (31 * (31 * serverId.hashCode() + itemId.hashCode()) + sizeBytes.hashCode()) * 31 +
            levels.contentHashCode()
}
