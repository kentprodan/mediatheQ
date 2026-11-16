package io.github.kentprodan.mediatheq.feature.video

import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.VideoDownload
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.toDownload
import io.github.kentprodan.mediatheq.core.data.unfollow
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Video
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Downloads for the movie and series pages (#279): a video goes as its original with its external text subtitle files
 * (from Room's streams); removing goes by origin, so an episode kept on its own survives its season.
 */
class VideoDownloader
    @Inject
    constructor(
        private val downloads: DownloadRepository,
        private val videos: VideoRepository,
    ) {
        /** Every video download by id. */
        val states: Flow<Map<MediaId, VideoDownload>> =
            downloads.videoDownloads.map { list ->
                list.associateBy { it.id }
            }

        suspend fun download(
            items: List<Video>,
            origin: DownloadOrigin,
        ) = downloads.downloadVideos(items.map { videos.toDownload(it) }, origin)

        /** Stops following [seriesId] (#383): its kept episodes go, unless downloaded otherwise. */
        suspend fun unfollow(seriesId: MediaId) = unfollow(videos, downloads, seriesId)

        /** A movie's or season's [origin] goes as a collection; an episode on its own only loses its single origin. */
        fun remove(
            origin: DownloadOrigin,
            ids: List<MediaId> = emptyList(),
        ) = if (origin ==
            DownloadOrigin.Single
        ) {
            downloads.removeOrigin(ids, origin)
        } else {
            downloads.removeCollection(origin)
        }
    }
