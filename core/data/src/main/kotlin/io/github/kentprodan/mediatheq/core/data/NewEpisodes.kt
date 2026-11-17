package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Video
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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

/** What to tell (#446) and what is known afterwards. */
data class EpisodeNotices(
    val new: List<Video>,
    val seen: Set<String>,
)

/**
 * The new unwatched episodes of the [followed] series against [seen] (#446). A series met for the first time (just
 * followed, or the first run) only records its episodes, so following a series never floods; an episode is told once.
 */
fun episodeNotices(
    seen: Set<String>,
    followed: Map<MediaId, List<Video>>,
): EpisodeNotices {
    val new = mutableListOf<Video>()
    val known = seen.toMutableSet()
    followed.forEach { (series, episodes) ->
        val seriesKey = SERIES_PREFIX + series
        val baseline = seriesKey !in known
        known += seriesKey
        episodes.forEach { episode ->
            val key = episode.id.toString()
            if (key !in known) {
                known += key
                if (!baseline && !episode.userData.played) new += episode
            }
        }
    }
    return EpisodeNotices(new, known)
}

private const val SERIES_PREFIX = "series:"

/**
 * Watches the followed series' episodes (#446): after a sync or a live update brings a new one, [notices] emits it
 * once (the seen ids are stored, so a restart never repeats them). The app posts the notifications.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Singleton
class NewEpisodeWatcher
    @Inject
    constructor(
        private val videos: VideoRepository,
        private val store: LibraryPreferencesStore,
        @param:ApplicationScope private val scope: CoroutineScope,
        @param:IoDispatcher private val io: CoroutineDispatcher,
    ) {
        private val mutableNotices = MutableSharedFlow<List<Video>>(extraBufferCapacity = 1)
        val notices: SharedFlow<List<Video>> = mutableNotices

        fun start() {
            scope.launch(io) {
                videos.followedSeries
                    .flatMapLatest { ids ->
                        if (ids.isEmpty()) {
                            flowOf(emptyMap())
                        } else {
                            combine(ids.map { id -> videos.allSeriesEpisodes(id).map { id to it } }) { it.toMap() }
                        }
                    }.map { followed -> followed.mapValues { (_, list) -> list.map { it.copy(overview = null) } } }
                    .distinctUntilChanged()
                    .debounce(QUIET)
                    .collect { followed ->
                        val result = episodeNotices(store.seenEpisodes.first().orEmpty(), followed)
                        store.setSeenEpisodes(result.seen)
                        if (result.new.isNotEmpty()) mutableNotices.emit(result.new)
                    }
            }
        }

        private companion object {
            val QUIET = 5.seconds
        }
    }
