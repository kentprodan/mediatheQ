package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import io.github.kentprodan.mediatheq.core.database.entity.UserDataColumns

/**
 * Per-user data (favorite, rating, plays) of one item, whatever its kind. Jellyfin ids are unique across kinds, so a
 * write goes to every library table and its staging twin: a sync in flight then swaps in the new value too.
 */
@Dao
interface UserDataDao {
    /** The stored user data of [id] (track, album or artist), `null` when it is not in the library. */
    @Query(
        """
        SELECT is_favorite, rating, play_count, last_played_at, played FROM track WHERE server_id = :serverId AND id = :id
        UNION ALL
        SELECT is_favorite, rating, play_count, last_played_at, played FROM album WHERE server_id = :serverId AND id = :id
        UNION ALL
        SELECT is_favorite, rating, play_count, last_played_at, played FROM artist WHERE server_id = :serverId AND id = :id
        LIMIT 1
        """,
    )
    suspend fun userData(
        serverId: String,
        id: String,
    ): UserDataColumns?

    /** Writes [data] on [id] in every table that holds it. */
    @Transaction
    suspend fun setUserData(
        serverId: String,
        id: String,
        data: UserDataColumns,
    ) {
        val (favorite, rating, playCount, lastPlayed, played) = data
        updateTrack(serverId, id, favorite, rating, playCount, lastPlayed, played)
        updateAlbum(serverId, id, favorite, rating, playCount, lastPlayed, played)
        updateArtist(serverId, id, favorite, rating, playCount, lastPlayed, played)
        updateTrackStaging(serverId, id, favorite, rating, playCount, lastPlayed, played)
        updateAlbumStaging(serverId, id, favorite, rating, playCount, lastPlayed, played)
        updateArtistStaging(serverId, id, favorite, rating, playCount, lastPlayed, played)
    }

    @Query(
        """
        UPDATE track SET is_favorite = :favorite, rating = :rating, play_count = :playCount,
        last_played_at = :lastPlayed, played = :played WHERE server_id = :serverId AND id = :id
        """,
    )
    suspend fun updateTrack(
        serverId: String,
        id: String,
        favorite: Boolean,
        rating: Double?,
        playCount: Int,
        lastPlayed: Long?,
        played: Boolean,
    )

    @Query(
        """
        UPDATE album SET is_favorite = :favorite, rating = :rating, play_count = :playCount,
        last_played_at = :lastPlayed, played = :played WHERE server_id = :serverId AND id = :id
        """,
    )
    suspend fun updateAlbum(
        serverId: String,
        id: String,
        favorite: Boolean,
        rating: Double?,
        playCount: Int,
        lastPlayed: Long?,
        played: Boolean,
    )

    @Query(
        """
        UPDATE artist SET is_favorite = :favorite, rating = :rating, play_count = :playCount,
        last_played_at = :lastPlayed, played = :played WHERE server_id = :serverId AND id = :id
        """,
    )
    suspend fun updateArtist(
        serverId: String,
        id: String,
        favorite: Boolean,
        rating: Double?,
        playCount: Int,
        lastPlayed: Long?,
        played: Boolean,
    )

    @Query(
        """
        UPDATE track_staging SET is_favorite = :favorite, rating = :rating, play_count = :playCount,
        last_played_at = :lastPlayed, played = :played WHERE server_id = :serverId AND id = :id
        """,
    )
    suspend fun updateTrackStaging(
        serverId: String,
        id: String,
        favorite: Boolean,
        rating: Double?,
        playCount: Int,
        lastPlayed: Long?,
        played: Boolean,
    )

    @Query(
        """
        UPDATE album_staging SET is_favorite = :favorite, rating = :rating, play_count = :playCount,
        last_played_at = :lastPlayed, played = :played WHERE server_id = :serverId AND id = :id
        """,
    )
    suspend fun updateAlbumStaging(
        serverId: String,
        id: String,
        favorite: Boolean,
        rating: Double?,
        playCount: Int,
        lastPlayed: Long?,
        played: Boolean,
    )

    @Query(
        """
        UPDATE artist_staging SET is_favorite = :favorite, rating = :rating, play_count = :playCount,
        last_played_at = :lastPlayed, played = :played WHERE server_id = :serverId AND id = :id
        """,
    )
    suspend fun updateArtistStaging(
        serverId: String,
        id: String,
        favorite: Boolean,
        rating: Double?,
        playCount: Int,
        lastPlayed: Long?,
        played: Boolean,
    )
}
