package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.data.nextEpisode
import io.github.kentprodan.mediatheq.core.data.nextUp
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * [VideoRepository] over in-memory lists; [requests] records each list read as "movies:Year:true". [failure], when
 * set, makes the list reads throw it (a failed database read).
 */
class FakeVideoRepository(
    override val hasVideo: Boolean = true,
) : VideoRepository {
    val movies = MutableStateFlow<List<Video>>(emptyList())
    val series = MutableStateFlow<List<Series>>(emptyList())
    val requests = mutableListOf<String>()
    var failure: Exception? = null

    override val movieCount: Flow<Int> = movies.map { it.size }
    override val seriesCount: Flow<Int> = series.map { it.size }

    private fun <T> read(
        name: String,
        source: Flow<T>,
    ): Flow<T> =
        flow {
            requests += name
            failure?.let { throw it }
            source.collect { emit(it) }
        }

    override fun movies(
        sort: VideoSort,
        unwatched: Boolean,
    ): Flow<List<Video>> =
        read("movies:$sort:$unwatched", movies.map { all -> all.filter { !unwatched || !it.userData.played } })

    override fun series(
        sort: VideoSort,
        unwatched: Boolean,
    ): Flow<List<Series>> =
        read("series:$sort:$unwatched", series.map { all -> all.filter { !unwatched || !it.userData.played } })

    override fun video(id: MediaId): Flow<Video?> = movies.map { all -> all.firstOrNull { it.id == id } }

    override fun series(id: MediaId): Flow<Series?> = series.map { all -> all.firstOrNull { it.id == id } }

    override fun seasons(seriesId: MediaId): Flow<List<Season>> = seriesSeasons.map { it[seriesId].orEmpty() }

    override fun episodes(seasonId: MediaId): Flow<List<Video>> = seasonEpisodes.map { it[seasonId].orEmpty() }

    /** Audio and subtitle streams by video id. */
    val streams = MutableStateFlow<Map<MediaId, List<MediaStreamInfo>>>(emptyMap())

    override fun streams(videoId: MediaId): Flow<List<MediaStreamInfo>> = streams.map { it[videoId].orEmpty() }

    /** Episodes by season id, for [episodes] and [nextUp]. */
    val seasonEpisodes = MutableStateFlow<Map<MediaId, List<Video>>>(emptyMap())

    /** Seasons by series id. */
    val seriesSeasons = MutableStateFlow<Map<MediaId, List<Season>>>(emptyMap())

    override fun nextUp(seriesId: MediaId): Flow<Video?> =
        seasonEpisodes.map { all -> nextUp(all.values.flatten().filter { it.seriesId == seriesId }) }

    /** What Home's video sections list (#275). */
    val watching = MutableStateFlow<List<Video>>(emptyList())
    val upNext = MutableStateFlow<List<Video>>(emptyList())
    var resumeRefreshes = 0

    override fun continueWatching(limit: Int): Flow<List<Video>> = watching.map { it.take(limit) }

    override fun nextUpEpisodes(limit: Int): Flow<List<Video>> = upNext.map { it.take(limit) }

    override fun recentlyAdded(
        kind: VideoKind,
        limit: Int,
    ): Flow<List<Video>> = movies.map { all -> all.filter { it.kind == kind && it.dateAdded != null }.take(limit) }

    override suspend fun nextEpisode(episode: MediaId): Video? {
        val all = seasonEpisodes.value.values.flatten()
        val current = all.firstOrNull { it.id == episode } ?: return null
        return nextEpisode(all.filter { it.seriesId == current.seriesId }, current)
    }

    override suspend fun refreshResume() {
        resumeRefreshes++
    }
}
