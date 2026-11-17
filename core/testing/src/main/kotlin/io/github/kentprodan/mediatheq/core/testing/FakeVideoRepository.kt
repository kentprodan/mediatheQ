package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.PersonVideos
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoSearchResults
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.data.nextEpisode
import io.github.kentprodan.mediatheq.core.data.nextUp
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaSegment
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.Person
import io.github.kentprodan.mediatheq.core.model.PlayerExtras
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoCollection
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

    /** What [people] and [similarMovies] answer, by item. */
    var peopleOf: Map<MediaId, List<Person>> = emptyMap()
    var similarOf: Map<MediaId, List<Video>> = emptyMap()

    override suspend fun people(itemId: MediaId): List<Person> = peopleOf[itemId].orEmpty()

    var peopleSearch: Map<String, List<Person>> = emptyMap()
    var videosOfPerson: Map<MediaId, PersonVideos> = emptyMap()

    override suspend fun searchPeople(
        query: String,
        limit: Int,
    ): List<Person> = peopleSearch[query].orEmpty().take(limit)

    override suspend fun personVideos(personId: MediaId): PersonVideos = videosOfPerson[personId] ?: PersonVideos()

    override suspend fun similarMovies(
        itemId: MediaId,
        limit: Int,
    ): List<Video> = similarOf[itemId].orEmpty().take(limit)

    val collections = MutableStateFlow<List<VideoCollection>>(emptyList())

    /** Collection id -> its movies, in order. */
    val collectionMovies = MutableStateFlow<Map<MediaId, List<Video>>>(emptyMap())

    override fun collections(): Flow<List<VideoCollection>> = collections

    override fun collection(id: MediaId): Flow<VideoCollection?> =
        collections.map { all ->
            all.firstOrNull {
                it.id ==
                    id
            }
        }

    override fun collectionMovies(id: MediaId): Flow<List<Video>> = collectionMovies.map { it[id].orEmpty() }

    /** What [search] answers; [searches] records each query. */
    var searchResult: MqResult<VideoSearchResults> = MqResult.Success(VideoSearchResults())
    val searches = mutableListOf<String>()

    override suspend fun search(
        query: String,
        limitPerKind: Int,
    ): MqResult<VideoSearchResults> {
        searches += query
        return searchResult
    }

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

    override fun allSeriesEpisodes(seriesId: MediaId): Flow<List<Video>> =
        seasonEpisodes.map { all -> all.values.flatten().filter { it.seriesId == seriesId } }

    /** The followed series (#383). */
    override val followedSeries = MutableStateFlow<Set<MediaId>>(emptySet())

    override suspend fun setFollowed(
        seriesId: MediaId,
        followed: Boolean,
    ) {
        followedSeries.value = if (followed) followedSeries.value + seriesId else followedSeries.value - seriesId
    }

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

    /** What [playerExtras] and [segments] answer per video (#277). */
    val extras = mutableMapOf<MediaId, PlayerExtras>()
    val segments = mutableMapOf<MediaId, List<MediaSegment>>()

    override suspend fun playerExtras(videoId: MediaId): PlayerExtras = extras[videoId] ?: PlayerExtras()

    override suspend fun segments(videoId: MediaId): List<MediaSegment> = segments[videoId].orEmpty()

    override suspend fun refreshResume() {
        resumeRefreshes++
    }
}
