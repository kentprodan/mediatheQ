package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.data.PlaybackRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlaybackReport
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tells the server what is playing (jellyfin skill, section 6): started when a track first plays, progress every
 * [interval] while playing and on pause or seek, stopped when it ends or is left. Stopped is never skipped: for a
 * transcode it is what ends the conversion on the server. Reports keep their order (one at a time, first come first
 * sent) and run on [scope], so the final one survives the playback service. Call it from the player's thread.
 */
class PlaybackReporter(
    private val repository: PlaybackRepository,
    private val streamOf: (MediaId) -> PlaybackStream?,
    private val scope: CoroutineScope,
    /** Where the position is read: the player's thread. */
    private val playerDispatcher: CoroutineDispatcher,
    private val interval: Duration = 10.seconds,
) {
    // One consumer sends reports one by one in the order the player produced them.
    private val reports = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var current: PlaybackStream? = null

    init {
        scope.launch { for (report in reports) report() }
    }

    private var ticker: Job? = null

    /** [itemId] is playing (first time: started; again after a pause: progress). [position] is read on the player thread. */
    fun playing(
        itemId: MediaId,
        position: () -> Duration,
    ) {
        // Positions are read when the event happens, not when the report is finally sent.
        val now = position()
        val active = current
        if (active == null || active.itemId != itemId) {
            if (active != null) end(active, now, failed = false)
            val stream = streamOf(itemId) ?: return
            current = stream
            send { repository.reportStart(PlaybackReport(stream, now, isPaused = false)) }
        } else {
            send { repository.reportProgress(PlaybackReport(active, now, isPaused = false)) }
        }
        ticker?.cancel()
        ticker =
            scope.launch(playerDispatcher) {
                while (isActive) {
                    delay(interval)
                    val stream = current ?: break
                    val at = position()
                    send { repository.reportProgress(PlaybackReport(stream, at, isPaused = false)) }
                }
            }
    }

    fun paused(position: Duration) {
        ticker?.cancel()
        val stream = current ?: return
        send { repository.reportProgress(PlaybackReport(stream, position, isPaused = true)) }
    }

    fun seeked(
        position: Duration,
        isPlaying: Boolean,
    ) {
        val stream = current ?: return
        send { repository.reportProgress(PlaybackReport(stream, position, isPaused = !isPlaying)) }
    }

    /** The current track ended, was skipped, replaced or failed at [position]. */
    fun ended(
        position: Duration,
        failed: Boolean = false,
    ) {
        ticker?.cancel()
        val stream = current ?: return
        current = null
        end(stream, position, failed)
    }

    private fun end(
        stream: PlaybackStream,
        position: Duration,
        failed: Boolean,
    ) {
        send { repository.reportStopped(PlaybackReport(stream, position, isPaused = false), failed) }
    }

    private fun send(report: suspend () -> Unit) {
        reports.trySend { report() }
    }
}
