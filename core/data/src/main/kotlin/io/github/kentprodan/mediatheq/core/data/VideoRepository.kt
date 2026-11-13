package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.dao.VideoDao
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.server.ServerCapabilities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultVideoRepository
    @Inject
    constructor(
        private val dao: VideoDao,
        private val filter: OfflineFilter,
        capabilities: ServerCapabilities,
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
