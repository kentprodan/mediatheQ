package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.database.dao.VideoDao
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import io.github.kentprodan.mediatheq.core.server.ServerCapabilities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** How the Movies and Series grids sort: by name, newest added first, or newest year first. */
enum class VideoSort { Name, DateAdded, Year }

/** The video library on the device (#270, ADR 0048); reads only, the sync writes it. */
interface VideoRepository {
    /** Whether the server has video at all (`ServerCapabilities.video`): the Library shows no video tiles otherwise. */
    val hasVideo: Boolean

    val movieCount: Flow<Int>
    val seriesCount: Flow<Int>

    fun movies(
        sort: VideoSort,
        unwatched: Boolean,
    ): Flow<List<Video>>

    fun series(
        sort: VideoSort,
        unwatched: Boolean,
    ): Flow<List<Series>>

    fun video(id: MediaId): Flow<Video?>

    fun series(id: MediaId): Flow<Series?>

    fun seasons(seriesId: MediaId): Flow<List<Season>>

    fun episodes(seasonId: MediaId): Flow<List<Video>>

    fun streams(videoId: MediaId): Flow<List<MediaStreamInfo>>

    /** The episode to watch next in [seriesId] ([nextUp]); `null` when every episode was watched. */
    fun nextUp(seriesId: MediaId): Flow<Video?>

    /** Movies and episodes left halfway, the last played first (Home, #275). */
    fun continueWatching(limit: Int): Flow<List<Video>>

    /** The next episode of each series in progress ([nextUpPerSeries], Home, #275). */
    fun nextUpEpisodes(limit: Int): Flow<List<Video>>

    /** The newest added movies or episodes (Home, #275). */
    fun recentlyAdded(
        kind: VideoKind,
        limit: Int,
    ): Flow<List<Video>>

    /** The episode after [episode] for auto-play ([nextEpisode]); `null` for a movie or after the last. */
    suspend fun nextEpisode(episode: MediaId): Video?

    /** Brings the resume points of what is halfway from the server into the rows (Home opens, a video stops; R26). */
    suspend fun refreshResume()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultVideoRepository
    @Inject
    constructor(
        private val dao: VideoDao,
        private val filter: OfflineFilter,
        capabilities: ServerCapabilities,
        private val source: LibrarySource,
        private val accountStore: AccountStore,
        private val userData: DefaultUserDataRepository,
    ) : VideoRepository {
        override val hasVideo = capabilities.video

        /**
         * [query] over everything, or, in offline mode, over the downloaded videos (ADR 0021). shortcut: no video is
         * downloaded before #279, so offline lists are empty; pass the completed video downloads' ids then.
         */
        private fun <T> offlineAware(query: (all: Boolean, downloaded: List<String>) -> Flow<T>): Flow<T> =
            filter.active.flatMapLatest { offline -> query(!offline, emptyList()) }

        override val movieCount: Flow<Int> = offlineAware(dao::movieCount)

        override val seriesCount: Flow<Int> = offlineAware(dao::seriesCount)

        override fun movies(
            sort: VideoSort,
            unwatched: Boolean,
        ): Flow<List<Video>> =
            offlineAware { all, downloaded -> dao.movies(all, downloaded, unwatched, sort.column) }
                .map { rows -> rows.map { it.toDomain() } }

        override fun series(
            sort: VideoSort,
            unwatched: Boolean,
        ): Flow<List<Series>> =
            offlineAware { all, downloaded -> dao.series(all, downloaded, unwatched, sort.column) }
                .map { rows -> rows.map { it.toDomain() } }

        override fun video(id: MediaId): Flow<Video?> = dao.video(id.itemId).map { it?.toDomain() }

        override fun series(id: MediaId): Flow<Series?> = dao.seriesById(id.itemId).map { it?.toDomain() }

        override fun seasons(seriesId: MediaId): Flow<List<Season>> =
            offlineAware { all, downloaded -> dao.seasons(seriesId.itemId, all, downloaded) }
                .map { rows -> rows.map { it.toDomain() } }

        override fun episodes(seasonId: MediaId): Flow<List<Video>> =
            offlineAware { all, downloaded -> dao.episodes(seasonId.itemId, all, downloaded) }
                .map { rows -> rows.map { it.toDomain() } }

        override fun nextUp(seriesId: MediaId): Flow<Video?> =
            offlineAware { all, downloaded -> dao.seriesEpisodes(seriesId.itemId, all, downloaded) }
                .map { rows -> nextUp(rows.map { it.toDomain() }) }

        override fun continueWatching(limit: Int): Flow<List<Video>> =
            offlineAware { all, downloaded -> dao.continueWatching(limit, all, downloaded) }
                .map { rows -> rows.map { it.toDomain() } }

        override fun nextUpEpisodes(limit: Int): Flow<List<Video>> =
            offlineAware { all, downloaded -> dao.allEpisodes(all, downloaded) }
                .map { rows -> nextUpPerSeries(rows.map { it.toDomain() }).take(limit) }

        override fun recentlyAdded(
            kind: VideoKind,
            limit: Int,
        ): Flow<List<Video>> =
            offlineAware { all, downloaded -> dao.recentlyAdded(kind.name, limit, all, downloaded) }
                .map { rows -> rows.map { it.toDomain() } }

        override suspend fun nextEpisode(episode: MediaId): Video? {
            val current = dao.video(episode.itemId).first()?.toDomain() ?: return null
            val series = current.seriesId ?: return null
            val episodes = dao.seriesEpisodes(series.itemId, all = true, downloaded = emptyList()).first()
            return nextEpisode(episodes.map { it.toDomain() }, current)
        }

        override suspend fun refreshResume() {
            if (!hasVideo) return
            val server = accountStore.signedInServer() ?: return
            // Best effort: a failure leaves the last synced positions, which the next sync or socket event refreshes.
            val resume = (source.resumeVideos(server, RESUME_LIMIT) as? MqResult.Success)?.value ?: return
            userData.applyFromServer(resume)
        }

        override fun streams(videoId: MediaId): Flow<List<MediaStreamInfo>> =
            dao.streams(videoId.itemId).map { rows -> rows.mapNotNull { it.toDomain() } }

        private val VideoSort.column
            get() =
                when (this) {
                    VideoSort.Name -> VideoDao.SORT_NAME
                    VideoSort.DateAdded -> VideoDao.SORT_ADDED
                    VideoSort.Year -> VideoDao.SORT_YEAR
                }
    }

/** As many as Home shows in a section at most. */
private const val RESUME_LIMIT = 50
