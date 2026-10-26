package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity

/**
 * A favorite or rating change of one item that the server has not confirmed yet (#56). Later changes to the same item
 * merge into the row, so only the last one is sent; [seq] orders the rows and grows with every change.
 */
@Entity(tableName = "pending_user_data", primaryKeys = ["server_id", "user_id", "item_id"])
data class PendingUserDataEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    /** The favorite flag to send; `null` when the favorite did not change. */
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean? = null,
    @ColumnInfo(name = "sets_rating") val setsRating: Boolean = false,
    val rating: Double? = null,
    /** What the item had before the first change, put back if the server refuses it. */
    @ColumnInfo(name = "before_favorite") val beforeFavorite: Boolean = false,
    @ColumnInfo(name = "before_rating") val beforeRating: Double? = null,
    val seq: Long = 0,
)
