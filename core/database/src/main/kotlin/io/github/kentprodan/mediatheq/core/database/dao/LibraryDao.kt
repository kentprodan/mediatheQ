package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Query
import androidx.room3.Relation
import io.github.kentprodan.mediatheq.core.database.entity.AlbumArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.SearchEntity
import kotlinx.coroutines.flow.Flow

/** Reads of the live library. Screens add their own queries in their issues. */
@Dao
interface LibraryDao {
    @Query("SELECT * FROM album ORDER BY sort_name, id")
    fun albums(): Flow<List<AlbumWithArtists>>

    /** [match] must already be a safe FTS5 expression (see `ftsMatchExpression` in `:core:data`). */
    @Query("SELECT * FROM search WHERE search MATCH :match ORDER BY rank LIMIT :limit")
    suspend fun search(
        match: String,
        limit: Int,
    ): List<SearchEntity>
}

/** An album with its artist credits; sort [credits] by position, Room does not order relations. */
data class AlbumWithArtists(
    @Embedded val album: AlbumEntity,
    @Relation(
        entity = AlbumArtistEntity::class,
        parentColumns = ["server_id", "id"],
        entityColumns = ["server_id", "album_id"],
    )
    val credits: List<AlbumCredit>,
)

/** One album credit and its artist; [artist] is `null` when the credited artist was not synced. */
data class AlbumCredit(
    @Embedded val link: AlbumArtistEntity,
    @Relation(parentColumns = ["server_id", "artist_id"], entityColumns = ["server_id", "id"])
    val artist: ArtistEntity?,
)
