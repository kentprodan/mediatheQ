// Adapted from Moonfin (https://github.com/Moonfin-Client/Moonfin-Core, packages/moonfin_native_video,
// DisplayModeSettle.kt), GPL-3.0. See THIRD_PARTY.md.
package io.github.kentprodan.mediatheq.feature.tv.video

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * When playback held for a display mode switch may start (#631). The start waits until the display and the audio devices
 * have been still for [quietMs], so the HDMI renegotiation has landed before an audio track opens (a track opened during
 * it fails, or Atmos decodes instead of passing through). Never past [maxWaitMs] from [startedAtMs]: a TV that never
 * settles must not keep playback from starting. Some TVs drop the HDMI audio route a second or two after the switch and
 * bring it back later; once it went, the start waits for it and runs [quietMs] after it returns. Pure: the caller owns
 * the clock.
 */
class DisplayModeSettle(
    private val quietMs: Long,
    maxWaitMs: Long,
    startedAtMs: Long,
) {
    private val deadlineMs = startedAtMs + maxWaitMs
    private var hdmiAudioSeen = false
    private var hdmiAudioAway = false

    /** Whether the HDMI audio route went during this hold. */
    var sawHdmiAudioDrop = false
        private set

    /** How long from [nowMs] (the latest change) until the start runs; 0 = now. */
    fun delayAfterChange(nowMs: Long): Long {
        val remaining = (deadlineMs - nowMs).coerceAtLeast(0L)
        return if (hdmiAudioAway) remaining else minOf(quietMs, remaining)
    }

    /** The HDMI audio route is [present] or not; only a route seen before can drop (Bluetooth alone never waits). */
    fun noteHdmiAudio(present: Boolean) {
        if (present) {
            hdmiAudioSeen = true
            hdmiAudioAway = false
        } else if (hdmiAudioSeen) {
            hdmiAudioAway = true
            sawHdmiAudioDrop = true
        }
    }
}

/**
 * Suspends until the display and the audio outputs settle after a mode switch (rules in [DisplayModeSettle]); listens
 * to [DisplayManager] and [AudioManager] on the main thread.
 */
internal suspend fun awaitDisplaySettled(context: Context) {
    val displays = context.getSystemService(DisplayManager::class.java)
    val audio = context.getSystemService(AudioManager::class.java)
    val changes = Channel<Unit>(Channel.CONFLATED)
    val settle = DisplayModeSettle(QUIET_MS, MAX_WAIT_MS, SystemClock.elapsedRealtime())
    val handler = Handler(Looper.getMainLooper())

    fun audioChanged() {
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        settle.noteHdmiAudio(outputs.any { it.type in HDMI_OUTPUTS })
        changes.trySend(Unit)
    }
    val displayListener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                changes.trySend(Unit)
            }
        }
    val audioCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = audioChanged()

            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = audioChanged()
        }
    displays.registerDisplayListener(displayListener, handler)
    audio.registerAudioDeviceCallback(audioCallback, handler)
    try {
        while (true) {
            val wait = settle.delayAfterChange(SystemClock.elapsedRealtime())
            if (wait <= 0L) break
            withTimeoutOrNull(wait) { changes.receive() } ?: break
        }
    } finally {
        displays.unregisterDisplayListener(displayListener)
        audio.unregisterAudioDeviceCallback(audioCallback)
    }
}

private val HDMI_OUTPUTS =
    setOf(AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC)
private const val QUIET_MS = 1_500L
private const val MAX_WAIT_MS = 8_000L
