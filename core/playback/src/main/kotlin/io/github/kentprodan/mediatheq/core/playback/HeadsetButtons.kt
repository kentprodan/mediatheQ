package io.github.kentprodan.mediatheq.core.playback

import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import io.github.kentprodan.mediatheq.core.data.HeadsetAction
import io.github.kentprodan.mediatheq.core.data.HeadsetPreferences
import io.github.kentprodan.mediatheq.core.data.NextButtonAction
import io.github.kentprodan.mediatheq.core.data.PreviousButtonAction

/** The time between the clicks of a double or triple click (#139). */
internal const val CLICK_WINDOW_MS = 350L
internal const val SLOW_CLICK_WINDOW_MS = 650L

/**
 * Counts the clicks of a headset's one button (#139): each click waits [window] for the next; the third one, or a
 * click that cannot be followed by more ([final]), ends the count at once. [onClicks] gets 1, 2 or 3.
 */
internal class ClickCounter(
    private val window: () -> Long,
    /** Runs the block after the delay; the result cancels it. */
    private val schedule: (Long, () -> Unit) -> () -> Unit,
    private val final: (clicks: Int) -> Boolean,
    private val onClicks: (Int) -> Unit,
) {
    private var clicks = 0
    private var pending: (() -> Unit)? = null

    fun click() {
        pending?.invoke()
        pending = null
        clicks++
        if (clicks >= MAX_CLICKS || final(clicks)) finish() else pending = schedule(window()) { finish() }
    }

    private fun finish() {
        val count = clicks
        clicks = 0
        pending = null
        onClicks(count)
    }

    private companion object {
        const val MAX_CLICKS = 3
    }
}

/** The action of [clicks] clicks. */
internal fun HeadsetPreferences.actionFor(clicks: Int): HeadsetAction =
    when (clicks) {
        1 -> singleClick
        2 -> doubleClick
        else -> tripleClick
    }

/** Whether a count of [clicks] cannot grow into anything: every longer click does nothing. */
internal fun HeadsetPreferences.isFinal(clicks: Int): Boolean =
    (clicks + 1..3).all { actionFor(it) == HeadsetAction.Nothing }

/**
 * Settings -> Playback -> Headset buttons (#139), for the media button events of headsets and Bluetooth devices: the
 * play button's clicks counted, next and previous remapped. Everything runs on [player], the session's player, so the
 * Playback behavior (#138) applies. Media3's own double-tap handling is replaced.
 */
@UnstableApi
internal class HeadsetButtons(
    private val player: Player,
    private val preferences: () -> HeadsetPreferences,
    schedule: (Long, () -> Unit) -> () -> Unit,
) {
    // The last play key: a Bluetooth device's PLAY or PAUSE means that, whatever the player shows.
    private var lastKey = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE

    private val counter =
        ClickCounter(
            window = { if (preferences().slowClicks) SLOW_CLICK_WINDOW_MS else CLICK_WINDOW_MS },
            schedule = schedule,
            final = { preferences().isFinal(it) },
            onClicks = { run(preferences().actionFor(it)) },
        )

    /** Handles [event] when it is one of our keys (both its down and its up); `false` leaves it to Media3. */
    fun onMediaButton(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (key !in HANDLED) return false
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return true
        when (key) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                runNext(preferences().nextButton)
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                runPrevious(preferences().previousButton)
            }

            else -> {
                lastKey = key
                counter.click()
            }
        }
        return true
    }

    private fun run(action: HeadsetAction) {
        when (action) {
            HeadsetAction.PlayPause -> {
                when (lastKey) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> Util.handlePlayButtonAction(player)
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> Util.handlePauseButtonAction(player)
                    else -> Util.handlePlayPauseButtonAction(player)
                }
            }

            HeadsetAction.Next -> {
                player.seekToNext()
            }

            HeadsetAction.Previous -> {
                player.seekToPrevious()
            }

            HeadsetAction.Nothing -> {
                Unit
            }
        }
    }

    private fun runNext(action: NextButtonAction) {
        when (action) {
            NextButtonAction.Next -> player.seekToNext()
            NextButtonAction.Previous -> player.seekToPrevious()
            NextButtonAction.SeekForward -> player.seekTo((player.currentPosition + SEEK_STEP_MS).coerceAtLeast(0))
            NextButtonAction.Nothing -> Unit
        }
    }

    private fun runPrevious(action: PreviousButtonAction) {
        when (action) {
            PreviousButtonAction.Previous -> player.seekToPrevious()
            PreviousButtonAction.Next -> player.seekToNext()
            PreviousButtonAction.SeekBack -> player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0))
            PreviousButtonAction.Nothing -> Unit
        }
    }

    private companion object {
        const val SEEK_STEP_MS = 10_000L

        val HANDLED =
            setOf(
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            )
    }
}
