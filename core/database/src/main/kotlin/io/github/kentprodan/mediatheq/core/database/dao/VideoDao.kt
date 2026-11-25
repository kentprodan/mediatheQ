package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.kentprodan.mediatheq.core.database.entity.FollowedSeriesEntity
import io.github.kentprodan.mediatheq.core.database.entity.SeasonEntity
import io.github.kentprodan.mediatheq.core.database.entity.SeriesEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoCollectionEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoStreamEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoSubtitleDelayEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoTrackChoiceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads of the video library (ADR 0048). Lists take `all` and `downloaded` like [LibraryDao] (ADR 0021): with `all`
 * false they keep only videos whose id is in `downloaded`, and the series and seasons holding one. `sort` is
 * [SORT_NAME], [SORT_ADDED] (newest first) or [SORT_YEAR] (newest first); ties go by name.
 */
@Dao
interface VideoDao {
    /** Collections with at least one item in the library (offline: one downloaded movie), by name (#426). */
    @Query(
        """
        SELECT * FROM video_collection c WHERE EXISTS (
            SELECT 1 FROM video_collection_item i JOIN video v ON v.server_id = i.server_id AND v.id = i.item_id
            WHERE i.server_id = c.server_id AND i.collection_id = c.id AND (:all OR v.id IN (:downloaded)))
        ORDER BY sort_name, id
        """,
    )
    fun collections(
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<VideoCollectionEntity>>

    @Query("SELECT * FROM video WHERE kind = 'Movie' AND id IN (:ids)")
    suspend fun moviesByIds(ids: List<String>): List<VideoEntity>

    @Query("SELECT * FROM series WHERE id IN (:ids)")
    suspend fun seriesByIds(ids: List<String>): List<SeriesEntity>

    @Query("SELECT * FROM video_collection WHERE id = :id")
    fun collection(id: String): Flow<VideoCollectionEntity?>

    /** The movies of collection [id] in its order (series in a collection are left out: the grid is of movies). */
    @Query(
        """
        SELECT v.* FROM video_collection_item i JOIN video v ON v.server_id = i.server_id AND v.id = i.item_id
        WHERE i.collection_id = :id AND v.kind = 'Movie' AND (:all OR v.id IN (:downloaded))
        ORDER BY i.position, v.sort_name, v.id
        """,
    )
    fun collectionMovies(
        id: String,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT * FROM video WHERE kind = 'Movie' AND (:all OR id IN (:downloaded)) AND (NOT :unwatched OR NOT played)
        ORDER BY CASE WHEN :sort = 'added' THEN date_added END DESC, CASE WHEN :sort = 'year' THEN year END DESC,
        sort_name, id
        """,
    )
    fun movies(
        all: Boolean,
        downloaded: List<String>,
        unwatched: Boolean,
        sort: String,
    ): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT * FROM series WHERE (:all OR EXISTS (
            SELECT 1 FROM video e WHERE e.server_id = series.server_id AND e.series_id = series.id
            AND e.id IN (:downloaded)
        )) AND (NOT :unwatched OR NOT played)
        ORDER BY CASE WHEN :sort = 'added' THEN date_added END DESC, CASE WHEN :sort = 'year' THEN year END DESC,
        sort_name, id
        """,
    )
    fun series(
        all: Boolean,
        downloaded: List<String>,
        unwatched: Boolean,
        sort: String,
    ): Flow<List<SeriesEntity>>

    @Query("SELECT COUNT(*) FROM video WHERE kind = 'Movie' AND (:all OR id IN (:downloaded))")
    fun movieCount(
        all: Boolean,
        downloaded: List<String>,
    ): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM series WHERE :all OR EXISTS (
            SELECT 1 FROM video e WHERE e.server_id = series.server_id AND e.series_id = series.id
            AND e.id IN (:downloaded)
        )
        """,
    )
    fun seriesCount(
        all: Boolean,
        downloaded: List<String>,
    ): Flow<Int>

    @Query(
        """
        SELECT * FROM season WHERE series_id = :seriesId AND (:all OR EXISTS (
            SELECT 1 FROM video e WHERE e.server_id = season.server_id AND e.season_id = season.id
            AND e.id IN (:downloaded)
        ))
        ORDER BY number IS NULL, number, title, id
        """,
    )
    fun seasons(
        seriesId: String,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<SeasonEntity>>

    @Query(
        """
        SELECT * FROM video WHERE kind = 'Episode' AND season_id = :seasonId AND (:all OR id IN (:downloaded))
        ORDER BY episode_number IS NULL, episode_number, sort_name, id
        """,
    )
    fun episodes(
        seasonId: String,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<VideoEntity>>

    /** Every episode of a series in watching order: by season, then episode, specials (season 0) last (#273, Next up). */
    @Query(
        """
        SELECT * FROM video WHERE kind = 'Episode' AND series_id = :seriesId AND (:all OR id IN (:downloaded))
        ORDER BY season_number IS NULL, season_number = 0, season_number, episode_number IS NULL, episode_number,
        sort_name, id
        """,
    )
    fun seriesEpisodes(
        seriesId: String,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<VideoEntity>>

    /** Movies and episodes left halfway, the last played first (Continue watching, #275). */
    @Query(
        """
        SELECT * FROM video WHERE position_ms > 0 AND NOT played AND (:all OR id IN (:downloaded))
        ORDER BY last_played_at IS NULL, last_played_at DESC, id LIMIT :limit
        """,
    )
    fun continueWatching(
        limit: Int,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<VideoEntity>>

    /** Every episode by series, each series in watching order (Next up on Home, #275). */
    @Query(
        """
        SELECT * FROM video WHERE kind = 'Episode' AND series_id IS NOT NULL AND (:all OR id IN (:downloaded))
        ORDER BY series_id, season_number IS NULL, season_number = 0, season_number, episode_number IS NULL,
        episode_number, sort_name, id
        """,
    )
    fun allEpisodes(
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<VideoEntity>>

    /** The newest added movies or episodes ([kind] `Movie` or `Episode`, #275). */
    @Query(
        """
        SELECT * FROM video WHERE kind = :kind AND date_added IS NOT NULL AND (:all OR id IN (:downloaded))
        ORDER BY date_added DESC, id LIMIT :limit
        """,
    )
    fun recentlyAdded(
        kind: String,
        limit: Int,
        all: Boolean,
        downloaded: List<String>,
    ): Flow<List<VideoEntity>>

    @Query("SELECT * FROM video WHERE id = :id")
    fun video(id: String): Flow<VideoEntity?>

    @Query("SELECT * FROM series WHERE id = :id")
    fun seriesById(id: String): Flow<SeriesEntity?>

    @Query("SELECT * FROM video_stream WHERE video_id = :videoId ORDER BY stream_index")
    fun streams(videoId: String): Flow<List<VideoStreamEntity>>

    /**
     * Movies ([kind] `movie`) or episodes (`episode`) whose title matches the FTS [match], best first (#325, diacritics
     * folded, ADR 0006); offline only downloaded ones. One kind per query, so neither crowds the other out.
     */
    @Query(
        """
        SELECT video.* FROM search
        JOIN video ON video.server_id = search.server_id AND video.id = search.item_id
        WHERE search MATCH :match AND search.kind = :kind AND (:all OR video.id IN (:downloaded))
        ORDER BY search.rank, video.sort_name, video.id LIMIT :limit
        """,
    )
    suspend fun searchVideos(
        match: String,
        kind: String,
        limit: Int,
        all: Boolean,
        downloaded: List<String>,
    ): List<VideoEntity>

    /** Series whose title matches, best first (#325); offline only those with a downloaded episode. */
    @Query(
        """
        SELECT series.* FROM search
        JOIN series ON series.server_id = search.server_id AND series.id = search.item_id
        WHERE search MATCH :match AND search.kind = 'series' AND (:all OR EXISTS (
            SELECT 1 FROM video e WHERE e.server_id = series.server_id AND e.series_id = series.id
            AND e.id IN (:downloaded)
        ))
        ORDER BY search.rank, series.sort_name, series.id LIMIT :limit
        """,
    )
    suspend fun searchSeries(
        match: String,
        limit: Int,
        all: Boolean,
        downloaded: List<String>,
    ): List<SeriesEntity>

    companion object {
        const val SORT_NAME = "name"
        const val SORT_ADDED = "added"
        const val SORT_YEAR = "year"
    }

    /** The tracks chosen for a movie, an episode or a season (#274). */
    @Query("SELECT * FROM video_track_choice WHERE server_id = :serverId AND item_id = :itemId")
    fun trackChoice(
        serverId: String,
        itemId: String,
    ): Flow<VideoTrackChoiceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTrackChoice(choice: VideoTrackChoiceEntity)

    @Query("DELETE FROM video_track_choice WHERE server_id = :serverId AND item_id = :itemId")
    suspend fun clearTrackChoice(
        serverId: String,
        itemId: String,
    )

    /** A movie's or episode's subtitle delay (#456); `null` when none was set. */
    @Query("SELECT delay_ms FROM video_subtitle_delay WHERE server_id = :serverId AND item_id = :itemId")
    fun subtitleDelay(
        serverId: String,
        itemId: String,
    ): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSubtitleDelay(delay: VideoSubtitleDelayEntity)

    @Query("DELETE FROM video_subtitle_delay WHERE server_id = :serverId AND item_id = :itemId")
    suspend fun clearSubtitleDelay(
        serverId: String,
        itemId: String,
    )

    /** The series followed for smart downloads (#383). */
    @Query("SELECT * FROM followed_series ORDER BY server_id, series_id")
    fun followedSeries(): Flow<List<FollowedSeriesEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun follow(series: FollowedSeriesEntity)

    @Query("DELETE FROM followed_series WHERE server_id = :serverId AND series_id = :seriesId")
    suspend fun unfollow(
        serverId: String,
        seriesId: String,
    )
}
