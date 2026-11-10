package io.github.kentprodan.mediatheq.core.playback

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.kentprodan.mediatheq.core.data.TransitionsPreferences

/** How far a fade of [durationMs] is after [elapsedMs]: 0 to 1, linear; a fade of no length is done at once. */
internal fun fadeProgress(
    elapsedMs: Long,
    durationMs: Int,
): Float = if (durationMs <= 0) 1f else (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)

/**
 * Settings -> Playback -> Transitions (#142): fades as a volume [factor] the service multiplies into the player
 * volume (with the normalization gain and the audio focus duck), so they work with offload.
 * - Fade in: the factor drops to 0 when play is asked for or a seek lands, and rises once audio really plays.
 * - Fade out ([fadeOutThen]): the session's player delays its pause or stop until the factor reached 0.
 * No fades while the volume is fixed ([fixedVolume]: bit-perfect).
 */
@UnstableApi
internal class VolumeFader(
    private val player: Player,
    private val settings: () -> TransitionsPreferences,
    private val fixedVolume: () -> Boolean,
    /** Runs a block after a delay; the result cancels it. */
    private val schedule: (Long, () -> Unit) -> () -> Unit,
    private val now: () -> Long = SystemClock::uptimeMillis,
    /** The factor changed: set the player volume again. */
    private val onChange: () -> Unit,
) : Player.Listener {
    /** What the player volume is multiplied by; always 1 while the volume is fixed. */
    val factor: Float
        get() = if (fixedVolume()) 1f else ramped

    private var ramped = 1f

    // Silent and waiting for audio to start, to fade in from there.
    private var armed = false

    // The pause or stop a running fade out ends with.
    private var pending: (() -> Unit)? = null
    private var cancelTick: (() -> Unit)? = null

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        if (playWhenReady) arm()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK && player.playWhenReady) arm()
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (armed && player.isPlaying) {
            armed = false
            ramp(to = 1f, settings().fadeInMs) {}
        }
    }

    /**
     * Fades out, then runs [action] (the pause or the stop). At once when fade out is off, nothing plays or the
     * volume is fixed. Asked again during the fade (a second pause, a stop after the pause), the fade is cut and both
     * actions run now.
     */
    fun fadeOutThen(action: () -> Unit) {
        if (pending != null) {
            finish()
            return action()
        }
        val duration = settings().fadeOutMs
        if (duration <= 0 || fixedVolume() || !player.isPlaying) return action()
        armed = false
        pending = action
        // From where the volume is: a fade out during a fade in takes its share of the time.
        ramp(to = 0f, (duration * ramped).toInt()) { finish() }
    }

    /** Ends a running fade out now: its pause or stop happens at once (a second pause, the app swiped away). */
    fun finish() {
        val action = pending ?: return
        pending = null
        stopRamp()
        action()
        set(1f)
    }

    /** Play during a fade out: the pause never happens and the volume comes back. */
    fun cancelFadeOut() {
        if (pending == null) return
        pending = null
        ramp(to = 1f, (settings().fadeOutMs * (1f - ramped)).toInt()) {}
    }

    /** Before the player is released: nothing pending runs any more. */
    fun release() {
        pending = null
        stopRamp()
    }

    private fun arm() {
        if (settings().fadeInMs <= 0 || fixedVolume() || pending != null) return
        stopRamp()
        armed = true
        set(0f)
    }

    private fun ramp(
        to: Float,
        durationMs: Int,
        done: () -> Unit,
    ) {
        stopRamp()
        val from = ramped
        val start = now()

        fun tick() {
            val progress = fadeProgress(now() - start, durationMs)
            set(from + (to - from) * progress)
            if (progress >= 1f) {
                cancelTick = null
                done()
            } else {
                cancelTick = schedule(TICK_MS, ::tick)
            }
        }
        tick()
    }

    private fun stopRamp() {
        cancelTick?.invoke()
        cancelTick = null
    }

    private fun set(value: Float) {
        if (ramped == value) return
        ramped = value
        onChange()
    }

    private companion object {
        // One volume step per display frame is smooth enough for fades of 100 ms and up.
        const val TICK_MS = 16L
    }
}
