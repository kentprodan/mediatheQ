package io.github.kentprodan.mediatheq.feature.video

import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.VideoDownload
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoToDownload
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.Video
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        ) = downloads.downloadVideos(
            items.map { video ->
                val subtitles =
                    videos
                        .streams(video.id)
                        .first()
                        .filter {
                            it.type == StreamType.Subtitle && it.isExternal &&
                                it.codec?.lowercase() in TEXT_SUBTITLES
                        }.map { it.index }
                VideoToDownload(video.id, video.title, subtitles)
            },
            origin,
        )

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

        private companion object {
            /** External subtitle files kept beside a downloaded video; picture formats are never external files. */
            val TEXT_SUBTITLES = setOf("subrip", "srt", "webvtt", "vtt", "ass", "ssa", "mov_text")
        }
    }
