package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.VideoPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.WatchedRemoval
import io.github.kentprodan.mediatheq.core.data.watchedRemovals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Removes watched video downloads made by hand (#413) per Settings -> Video: a pass when the downloads or the setting
 * change, and every hour for the delays. Lives in `:core:playback` because it must spare what plays now.
 * shortcut: a play reported offline and not yet sent is not waited for; the server's watched flag decides once synced.
 */
@Singleton
class WatchedDownloadCleaner
    @Inject
    constructor(
        private val downloads: DownloadRepository,
        private val videos: VideoRepository,
        private val preferences: VideoPreferencesRepository,
        private val playback: PlaybackConnection,
        private val clock: Clock,
        @param:ApplicationScope private val scope: CoroutineScope,
        @param:IoDispatcher private val io: CoroutineDispatcher,
    ) {
        fun start() {
            scope.launch(io) {
                val hourly =
                    flow {
                        while (true) {
                            emit(Unit)
                            delay(1.hours)
                        }
                    }
                combine(downloads.videoDownloads, preferences.preferences, hourly) { list, prefs, _ -> list to prefs }
                    .collect { (list, prefs) ->
                        if (prefs.watchedRemoval == WatchedRemoval.Off || list.isEmpty()) return@collect
                        val known = list.mapNotNull { d -> videos.video(d.id).first()?.let { d.id to it } }.toMap()
                        val plan =
                            watchedRemovals(
                                list,
                                known,
                                prefs.watchedRemoval,
                                prefs.watchedRemovalSince,
                                clock.now(),
                                playback.state.value.item
                                    ?.id,
                            )
                        plan.forEach { (id, origins) -> origins.forEach { downloads.removeOrigin(listOf(id), it) } }
                    }
            }
        }
    }
