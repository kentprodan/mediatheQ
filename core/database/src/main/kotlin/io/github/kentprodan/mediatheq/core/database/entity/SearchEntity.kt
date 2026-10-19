package io.github.kentprodan.mediatheq.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Fts5

/**
 * Full-text index over names, rebuilt with every library swap. FTS5's default `unicode61` tokenizer folds diacritics,
 * so "Mosoi" finds "Moșoi" and "Moşoi" (ADR 0006).
 */
@Fts5(notIndexed = ["kind", "server_id", "item_id"])
@Entity(tableName = "search")
data class SearchEntity(
    /** `artist`, `album`, `track` or `playlist`. */
    val kind: String,
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    val name: String,
)
