package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Video
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

    override fun seasons(seriesId: MediaId): Flow<List<Season>> = MutableStateFlow(emptyList())

    override fun episodes(seasonId: MediaId): Flow<List<Video>> = MutableStateFlow(emptyList())

    override fun streams(videoId: MediaId): Flow<List<MediaStreamInfo>> = MutableStateFlow(emptyList())
}
