package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/** What a sleep timer waits for (#378). */
sealed interface SleepTimerChoice {
    /** The end of the [count]th track, the playing one counted as the first. */
    data class Tracks(
        val count: Int,
    ) : SleepTimerChoice

    /** [minutes] (1-60) from the start. */
    data class Minutes(
        val minutes: Int,
    ) : SleepTimerChoice

    /** The next [hour]:[minute] on the phone's clock. */
    data class At(
        val hour: Int,
        val minute: Int,
    ) : SleepTimerChoice
}

/** A running sleep timer: [tracksLeft] for [SleepTimerChoice.Tracks] (the playing one included), else [endsAt]. */
data class SleepTimerState(
    val choice: SleepTimerChoice,
    val tracksLeft: Int? = null,
    val endsAt: Instant? = null,
)

/**
 * The sleep timer (#378): screens [start] and [cancel] it, the playback service [attach]es its player, which pauses
 * when the timer runs out (with the Transitions fade out, since the session's player fades its pauses). The last
 * choice is remembered on this device ([last], read off the main thread). Main thread only, except that read. The
 * constructor with the stored choice's reader and writer is for tests. The playback service stopping ends a timer:
 * nothing plays any more.
 */
@Singleton
class SleepTimer(
    private val readLast: () -> String?,
    private val writeLast: (String) -> Unit,
    private val now: () -> Instant = { Clock.System.now() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** Where the stored choice is read; the tests read it at once. */
    loadLast: (() -> Unit) -> Unit = { it() },
) {
    // Like the saved-queue flag (ADR 0020): one small value of this device, not a setting.
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(
        { context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE).getString(LAST_KEY, null) },
        { value ->
            context
                .getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
                .edit()
                .putString(LAST_KEY, value)
                .apply()
        },
        loadLast = { read -> scope.launch(io) { read() } },
    )

    private val running = MutableStateFlow<SleepTimerState?>(null)

    /** The running timer; `null` when off. */
    val state: StateFlow<SleepTimerState?> = running.asStateFlow()

    private val lastChoice = MutableStateFlow<SleepTimerChoice?>(null)

    init {
        // A choice started meanwhile is newer than the stored one.
        loadLast { readLast()?.let(::decodeSleepTimerChoice)?.let { lastChoice.compareAndSet(null, it) } }
    }

    /** The choice last started, offered first. */
    val last: StateFlow<SleepTimerChoice?> = lastChoice.asStateFlow()

    private var attached: Attached? = null

    /** Starts [choice], replacing a running timer. */
    fun start(choice: SleepTimerChoice) {
        lastChoice.value = choice
        writeLast(encodeSleepTimerChoice(choice))
        running.value =
            when (choice) {
                is SleepTimerChoice.Tracks -> SleepTimerState(choice, tracksLeft = choice.count.coerceAtLeast(1))
                is SleepTimerChoice.Minutes -> SleepTimerState(choice, endsAt = now() + choice.minutes.minutes)
                is SleepTimerChoice.At -> SleepTimerState(choice, endsAt = nextOccurrence(now(), choice, zone()))
            }
        attached?.arm()
    }

    fun cancel() {
        running.value = null
        attached?.arm()
    }

    /**
     * Connects the service's [player] (the session's, so a pause fades out); [pauseAtEnd] sets the ExoPlayer's
     * pause at the end of the playing item, for the last counted track. [schedule] runs a block after a delay and
     * returns its cancel. The result detaches.
     */
    internal fun attach(
        player: Player,
        pauseAtEnd: (Boolean) -> Unit,
        schedule: (Long, () -> Unit) -> () -> Unit,
    ): () -> Unit {
        val link = Attached(player, pauseAtEnd, schedule)
        attached = link
        player.addListener(link)
        link.arm()
        return {
            player.removeListener(link)
            link.disarm()
            if (attached === link) {
                attached = null
                running.value = null
            }
        }
    }

    private inner class Attached(
        private val player: Player,
        private val pauseAtEnd: (Boolean) -> Unit,
        private val schedule: (Long, () -> Unit) -> () -> Unit,
    ) : Player.Listener {
        private var cancelAlarm: (() -> Unit)? = null

        /** Follows [running]: the alarm for a time, the pause at the end for the last track. */
        fun arm() {
            disarm()
            val timer = running.value ?: return
            timer.endsAt?.let { endsAt ->
                val delay = (endsAt - now()).inWholeMilliseconds
                // Ran out while no player was attached (the service stopped): nothing to pause any more.
                if (delay <= 0) return finish()
                cancelAlarm = schedule(delay) { expire() }
            }
            pauseAtEnd(timer.tracksLeft == 1)
        }

        fun disarm() {
            cancelAlarm?.invoke()
            cancelAlarm = null
            pauseAtEnd(false)
        }

        private fun expire() {
            cancelAlarm = null
            finish()
            if (player.playWhenReady) player.pause()
        }

        private fun finish() {
            running.value = null
            disarm()
        }

        override fun onMediaItemTransition(
            mediaItem: androidx.media3.common.MediaItem?,
            reason: Int,
        ) {
            // A new queue is a new start, not a track played.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
            val timer = running.value ?: return
            val left = timer.tracksLeft ?: return
            if (left <= 1) {
                // Skipped past the last counted track: stop here.
                finish()
                if (player.playWhenReady) player.pause()
                return
            }
            running.value = timer.copy(tracksLeft = left - 1)
            pauseAtEnd(left - 1 == 1)
        }

        // A handler delay counts uptime, which stops while the phone sleeps paused: measure again from the clock.
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && running.value?.endsAt != null) arm()
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && running.value?.tracksLeft != null) {
                finish()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // The queue ran out first: nothing left to stop.
            if (playbackState == Player.STATE_ENDED && running.value?.tracksLeft != null) finish()
        }
    }

    private companion object {
        const val LAST_KEY = "last"
    }
}

/** The first [SleepTimerChoice.At] time after [now] in [zone]: today, or tomorrow when it has passed. */
internal fun nextOccurrence(
    now: Instant,
    at: SleepTimerChoice.At,
    zone: ZoneId,
): Instant {
    val local = now.toJavaInstant().atZone(zone)
    var target =
        local
            .withHour(at.hour)
            .withMinute(at.minute)
            .withSecond(0)
            .withNano(0)
    if (!target.isAfter(local)) target = target.plusDays(1)
    return target.toInstant().toKotlinInstant()
}

/** "tracks:2", "minutes:30" or "at:23:15". */
internal fun encodeSleepTimerChoice(choice: SleepTimerChoice): String =
    when (choice) {
        is SleepTimerChoice.Tracks -> "tracks:${choice.count}"
        is SleepTimerChoice.Minutes -> "minutes:${choice.minutes}"
        is SleepTimerChoice.At -> "at:${choice.hour}:${choice.minute}"
    }

/** The choice [encodeSleepTimerChoice] wrote; `null` for anything else. */
internal fun decodeSleepTimerChoice(value: String): SleepTimerChoice? {
    val parts = value.split(':')
    val numbers = parts.drop(1).map { it.toIntOrNull() ?: return null }
    return when {
        parts[0] == "tracks" && numbers.size == 1 && numbers[0] in 1..MAX_TRACKS -> {
            SleepTimerChoice.Tracks(numbers[0])
        }

        parts[0] == "minutes" && numbers.size == 1 && numbers[0] in 1..MAX_MINUTES -> {
            SleepTimerChoice.Minutes(numbers[0])
        }

        parts[0] == "at" && numbers.size == 2 && numbers[0] in 0..23 && numbers[1] in 0..59 -> {
            SleepTimerChoice.At(numbers[0], numbers[1])
        }

        else -> {
            null
        }
    }
}

/** The most minutes a [SleepTimerChoice.Minutes] runs. */
const val MAX_MINUTES = 60

/** The most tracks a [SleepTimerChoice.Tracks] counts. */
const val MAX_TRACKS = 10
