package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.Video
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** What one smart downloads pass does (#383). */
data class SmartDownloadPlan(
    /** Series -> its episodes to download now. */
    val download: Map<MediaId, List<Video>> = emptyMap(),
    /** Series -> its episodes no longer among the next ones (watched): they lose the series' origin. */
    val release: Map<MediaId, List<MediaId>> = emptyMap(),
)

/** How many next episodes of a followed series stay downloaded (#383). */
const val SMART_EPISODES = 3

/**
 * The next [count] episodes to keep of a series' [episodes] (watching order): from Next up on, the unwatched ones;
 * specials are not part of the run. None once everything is watched.
 */
fun nextEpisodes(
    episodes: List<Video>,
    count: Int = SMART_EPISODES,
): List<Video> {
    val run = episodes.filter { it.seasonNumber != 0 }
    val start = nextUp(run) ?: return emptyList()
    return run.dropWhile { it.id != start.id }.filter { !it.userData.played }.take(count)
}

/**
 * The pass (#383): each followed series keeps its [nextEpisodes] with its own origin; the ones it no longer needs lose
 * it (deleted unless the user downloaded them too). Only followed series are touched: a series missing from [followed]
 * (signed out, another account) keeps its episodes; unfollowing removes them itself.
 */
fun smartDownloadPlan(
    followed: Map<MediaId, List<Video>>,
    downloads: List<VideoDownload>,
    count: Int = SMART_EPISODES,
): SmartDownloadPlan {
    val kept =
        downloads
            .flatMap { download ->
                download.origins.filterIsInstance<DownloadOrigin.FollowedSeries>().map { it.id to download.id }
            }.groupBy({ it.first }, { it.second })
    val download = mutableMapOf<MediaId, List<Video>>()
    val release = mutableMapOf<MediaId, List<MediaId>>()
    followed.forEach { (series, episodes) ->
        val wanted = nextEpisodes(episodes, count)
        val has = kept[series].orEmpty().toSet()
        wanted.filter { it.id !in has }.takeIf { it.isNotEmpty() }?.let { download[series] = it }
        (has - wanted.map { it.id }.toSet()).takeIf { it.isNotEmpty() }?.let { release[series] = it.toList() }
    }
    return SmartDownloadPlan(download, release)
}

/** Stops following [seriesId] (#383) and lets go of the episodes it kept (deleted unless kept otherwise). */
suspend fun unfollow(
    videos: VideoRepository,
    downloads: DownloadRepository,
    seriesId: MediaId,
) {
    videos.setFollowed(seriesId, false)
    downloads.removeCollection(DownloadOrigin.FollowedSeries(seriesId))
}

/**
 * Smart downloads (#383): a pass runs when the followed series, their episodes (a watched one, a new one after a sync)
 * or the video downloads change. Downloads follow Settings -> Offline (Wi-Fi only and the rest) like any other.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Singleton
class SmartDownloads
    @Inject
    constructor(
        private val videos: VideoRepository,
        private val downloads: DownloadRepository,
        @ApplicationScope private val scope: CoroutineScope,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /** For the app's lifetime; call once from the Application. */
        fun start() {
            scope.launch(ioDispatcher) {
                // The download index first: before it is read, every finished episode would look missing.
                downloads.completedIds.first()
                videos.followedSeries
                    .flatMapLatest { ids ->
                        if (ids.isEmpty()) {
                            flowOf(emptyMap())
                        } else {
                            combine(ids.map { id -> videos.allSeriesEpisodes(id).map { id to it } }) { it.toMap() }
                        }
                    }.combine(downloads.videoDownloads) { followed, list -> smartDownloadPlan(followed, list) }
                    // Progress ticks give the same plan: only a change of plan waits, and a sync makes one pass.
                    .distinctUntilChanged()
                    .debounce(PASS_DELAY)
                    .collect(::apply)
            }
        }

        private suspend fun apply(plan: SmartDownloadPlan) {
            plan.download.forEach { (series, episodes) ->
                downloads.downloadVideos(episodes.map { videos.toDownload(it) }, DownloadOrigin.FollowedSeries(series))
            }
            plan.release.forEach { (series, ids) -> downloads.removeOrigin(ids, DownloadOrigin.FollowedSeries(series)) }
        }

        private companion object {
            val PASS_DELAY = 2.seconds
        }
    }

/** [video] as a download (#279): the original with its external text subtitle files, from Room's streams. */
suspend fun VideoRepository.toDownload(video: Video): VideoToDownload {
    val subtitles =
        streams(video.id)
            .first()
            .filter { it.type == StreamType.Subtitle && it.isExternal && it.codec?.lowercase() in TEXT_SUBTITLES }
            .map { it.index }
    return VideoToDownload(video.id, video.title, subtitles)
}

/** External subtitle files kept beside a downloaded video; picture formats are never external files. */
private val TEXT_SUBTITLES = setOf("subrip", "srt", "webvtt", "vtt", "ass", "ssa", "mov_text")
