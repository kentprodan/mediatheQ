package io.github.kentprodan.mediatheq.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import io.github.kentprodan.mediatheq.core.database.entity.SeasonEntity
import io.github.kentprodan.mediatheq.core.database.entity.SeriesEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoStreamEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads of the video library (ADR 0048). Lists take `all` and `downloaded` like [LibraryDao] (ADR 0021): with `all`
 * false they keep only videos whose id is in `downloaded`, and the series and seasons holding one. `sort` is
 * [SORT_NAME], [SORT_ADDED] (newest first) or [SORT_YEAR] (newest first); ties go by name.
 */
@Dao
interface VideoDao {
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

    @Query("SELECT * FROM video WHERE id = :id")
    fun video(id: String): Flow<VideoEntity?>

    @Query("SELECT * FROM series WHERE id = :id")
    fun seriesById(id: String): Flow<SeriesEntity?>

    @Query("SELECT * FROM video_stream WHERE video_id = :videoId ORDER BY stream_index")
    fun streams(videoId: String): Flow<List<VideoStreamEntity>>

    /** Movies, series and episodes whose title matches the FTS [match], best first (diacritics folded, ADR 0006). */
    @Query(
        """
        SELECT item_id FROM search WHERE search MATCH :match AND kind IN ('movie', 'series', 'episode')
        ORDER BY rank, name
        """,
    )
    suspend fun searchIds(match: String): List<String>

    companion object {
        const val SORT_NAME = "name"
        const val SORT_ADDED = "added"
        const val SORT_YEAR = "year"
    }
}
