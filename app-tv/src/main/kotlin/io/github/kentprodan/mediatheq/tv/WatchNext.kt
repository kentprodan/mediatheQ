package io.github.kentprodan.mediatheq.tv

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.TvContractCompat.PreviewPrograms
import androidx.tvprovider.media.tv.TvContractCompat.WatchNextPrograms
import androidx.tvprovider.media.tv.WatchNextProgram
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.data.ArtworkUrlResolver
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.resumePosition
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** One card of the Google TV home's Continue watching row (#386). */
data class WatchNextEntry(
    val video: Video,
    /** Started and left halfway; else the next episode of a series in progress. */
    val continuing: Boolean,
)

/** What to change in the system's Watch Next row: [insert] new cards, [update] kept ones by program id, [delete] gone. */
data class WatchNextPlan(
    val insert: List<WatchNextEntry>,
    val update: Map<Long, WatchNextEntry>,
    val delete: List<Long>,
)

/** Pure (#386): the published programs (item id -> program id) against what should show now. */
fun watchNextPlan(
    published: Map<String, Long>,
    wanted: List<WatchNextEntry>,
): WatchNextPlan {
    val wantedIds = wanted.map { it.video.id.itemId }.toSet()
    return WatchNextPlan(
        insert = wanted.filter { it.video.id.itemId !in published },
        update = wanted.mapNotNull { entry -> published[entry.video.id.itemId]?.let { it to entry } }.toMap(),
        delete = published.filterKeys { it !in wantedIds }.values.toList(),
    )
}

/** The deep link a card opens: `mediatheq://video/<server>/<item>?position=<ms>`. */
fun watchNextUri(
    id: MediaId,
    position: Duration,
): Uri =
    Uri
        .Builder()
        .scheme(SCHEME)
        .authority(HOST)
        .appendPath(id.serverId.value)
        .appendPath(id.itemId)
        .appendQueryParameter(POSITION, position.inWholeMilliseconds.toString())
        .build()

/** The video and the position a [watchNextUri] names; `null` for any other link. */
fun parseWatchNextUri(uri: Uri?): Pair<MediaId, Duration>? {
    if (uri == null || uri.scheme != SCHEME || uri.host != HOST) return null
    val (server, item) = uri.pathSegments.takeIf { it.size == 2 } ?: return null
    val position = uri.getQueryParameter(POSITION)?.toLongOrNull()?.milliseconds ?: Duration.ZERO
    return MediaId(ServerId(server), item) to position
}

private const val SCHEME = "mediatheq"
private const val HOST = "video"
private const val POSITION = "position"

/**
 * Keeps the Google TV home's Continue watching row in step with the library (#386): movies and episodes left halfway
 * and each series' next episode, refreshed when they change; a card opens the player there. Program ids are kept in
 * a small preferences file, as the provider owns the rows.
 */
@OptIn(FlowPreview::class)
@Singleton
class WatchNextPublisher
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val videos: VideoRepository,
        private val artwork: ArtworkUrlResolver,
        @param:ApplicationScope private val scope: CoroutineScope,
        @param:IoDispatcher private val io: CoroutineDispatcher,
        private val log: AppLog,
    ) {
        private val prefs by lazy { context.getSharedPreferences(WATCH_NEXT_FILE, Context.MODE_PRIVATE) }

        fun start() {
            scope.launch(io) {
                combine(videos.continueWatching(LIMIT), videos.nextUpEpisodes(LIMIT)) { watching, next ->
                    watching.map { WatchNextEntry(it, continuing = true) } +
                        next
                            .filter { n -> watching.none { it.id == n.id } }
                            .map { WatchNextEntry(it, continuing = false) }
                }.distinctUntilChanged()
                    .debounce(QUIET)
                    .collect { entries ->
                        runCatching { publish(entries) }.onFailure { log.warn(TAG, "Watch Next not updated", it) }
                    }
            }
        }

        @SuppressLint("RestrictedApi")
        private suspend fun publish(wanted: List<WatchNextEntry>) {
            val published = prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
            val plan = watchNextPlan(published, wanted)
            val resolver = context.contentResolver
            plan.delete.forEach { resolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null) }
            val ids = published.filterValues { it !in plan.delete }.toMutableMap()
            plan.update.forEach { (programId, entry) ->
                val uri = TvContractCompat.buildWatchNextProgramUri(programId)
                resolver.update(uri, program(entry).toContentValues(), null, null)
            }
            plan.insert.forEach { entry ->
                val uri = resolver.insert(WatchNextPrograms.CONTENT_URI, program(entry).toContentValues())
                uri?.let { ids[entry.video.id.itemId] = android.content.ContentUris.parseId(it) }
            }
            prefs.edit(commit = true) {
                clear()
                ids.forEach { (item, program) -> putLong(item, program) }
            }
        }

        // tvprovider 1.0.0 marks its own column constants restricted (a known false positive of its lint rule).
        @SuppressLint("RestrictedApi")
        private suspend fun program(entry: WatchNextEntry): WatchNextProgram {
            val video = entry.video
            val episode = video.kind == VideoKind.Episode
            val position = video.resumePosition()
            val image = if (episode) video.backdrop ?: video.artwork else video.artwork
            val poster = image?.let { artwork.url(it, POSTER_WIDTH) }
            val type = if (episode) PreviewPrograms.TYPE_TV_EPISODE else PreviewPrograms.TYPE_MOVIE
            val next = if (entry.continuing) CONTINUE else NEXT
            return WatchNextProgram
                .Builder()
                .setType(type)
                .setWatchNextType(next)
                .setTitle(if (episode) video.seriesName ?: video.title else video.title)
                .apply {
                    if (episode) {
                        setEpisodeTitle(video.title)
                        video.seasonNumber?.let { setSeasonNumber(it) }
                        video.episodeNumber?.let { setEpisodeNumber(it) }
                    }
                    poster?.let { setPosterArtUri(Uri.parse(it)) }
                    if (episode) setPosterArtAspectRatio(PreviewPrograms.ASPECT_RATIO_16_9)
                }.setDescription(video.overview)
                .setDurationMillis(video.duration.inWholeMilliseconds.toInt())
                .setLastPlaybackPositionMillis(position.inWholeMilliseconds.toInt())
                .setLastEngagementTimeUtcMillis(
                    video.userData.lastPlayedAt?.toEpochMilliseconds() ?: System.currentTimeMillis(),
                ).setInternalProviderId(video.id.itemId)
                .setIntentUri(watchNextUri(video.id, position))
                .build()
        }

        private companion object {
            const val TAG = "WatchNext"
            const val CONTINUE = WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
            const val NEXT = WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
            const val WATCH_NEXT_FILE = "watch_next"
            const val LIMIT = 10
            const val POSTER_WIDTH = 640
            val QUIET = 5.seconds
        }
    }
