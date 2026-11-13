package io.github.kentprodan.mediatheq.core.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaNotification
import io.github.kentprodan.mediatheq.core.data.AutoActionsPreferences
import io.github.kentprodan.mediatheq.core.model.OutputKind

/** What Settings -> Playback -> Automatic actions reacts to (#141). */
internal enum class AutoEvent {
    /** Wired or USB headphones (or a USB DAC) connected. */
    HeadphonesConnected,

    /** A Bluetooth headphone, speaker or car connected. */
    BluetoothConnected,

    /** The media volume reached 0. */
    VolumeZero,

    /** The media volume left 0. */
    VolumeRaised,

    /** The app was swiped away. */
    TaskRemoved,

    /** A controller (car, watch, headset key) asked to stop. */
    RemoteStop,
}

internal enum class AutoAction { Play, Pause, Nothing }

/** What [autoAction] knows about the player. */
internal data class AutoState(
    /** Music is meant to play ([wantsToPlay]). */
    val playing: Boolean,
    val hasQueue: Boolean,
    /** The last pause was the volume reaching 0, and nobody played or paused since. */
    val pausedByVolume: Boolean = false,
)

/**
 * Event + settings + state -> play, pause or nothing. Playing needs something queued (signed out, the queue is
 * empty); for [AutoEvent.RemoteStop], Pause replaces the stop and Nothing lets it through.
 */
internal fun autoAction(
    event: AutoEvent,
    settings: AutoActionsPreferences,
    state: AutoState,
): AutoAction {
    val play = if (!state.playing && state.hasQueue) AutoAction.Play else AutoAction.Nothing
    val pause = if (state.playing) AutoAction.Pause else AutoAction.Nothing
    return when (event) {
        AutoEvent.HeadphonesConnected -> if (settings.headphonePlay) play else AutoAction.Nothing
        AutoEvent.BluetoothConnected -> if (settings.bluetoothPlay) play else AutoAction.Nothing
        AutoEvent.VolumeZero -> if (settings.volumePause) pause else AutoAction.Nothing
        AutoEvent.VolumeRaised -> if (settings.volumePause && state.pausedByVolume) play else AutoAction.Nothing
        AutoEvent.TaskRemoved -> if (settings.pauseOnTaskRemoval) pause else AutoAction.Nothing
        AutoEvent.RemoteStop -> if (settings.ignoreRemoteStop) AutoAction.Pause else AutoAction.Nothing
    }
}

/** The event an added audio device of [type] means; `null` for the phone's own outputs and call-only devices. */
internal fun connectEvent(type: Int): AutoEvent? =
    when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        -> {
            AutoEvent.HeadphonesConnected
        }

        else -> {
            when (outputKind(type)) {
                OutputKind.Usb -> AutoEvent.HeadphonesConnected
                OutputKind.Bluetooth -> AutoEvent.BluetoothConnected
                OutputKind.Phone, OutputKind.Hdmi -> null
            }
        }
    }

/**
 * Automatic actions inside the playback service (#141, ADR 0033): they work while the service lives. Outputs are
 * watched with an [AudioDeviceCallback] (wired, USB, Bluetooth, LE Audio; no permission) and the volume with the
 * platform's volume broadcast. A play the platform refuses (no audio focus once the service left the foreground)
 * does nothing.
 */
@UnstableApi
internal class AutoActionsController(
    private val context: Context,
    private val audioManager: AudioManager,
    private val player: Player,
    private val settings: () -> AutoActionsPreferences,
) : Player.Listener {
    private var pausedByVolume = false

    // Our own pause or play, as the listener will see it: not the user's.
    private var expected: Boolean? = null
    private var volumeZero = isVolumeZero()

    // The media card was swiped away: the stop that follows is a real one.
    private var dismissed = false

    // Outputs already there are not "connected" now. A device gets a new id on every connect.
    private val known = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.id }.toSet()

    private val devices =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) =
                onOutputsAdded(addedDevices.filter { it.isSink && it.id !in known }.map { it.type })

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onOutputsRemoved()
        }

    private val volume =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val zero = isVolumeZero()
                if (zero == volumeZero) return
                volumeZero = zero
                on(if (zero) AutoEvent.VolumeZero else AutoEvent.VolumeRaised)
            }
        }

    fun start() {
        player.addListener(this)
        audioManager.registerAudioDeviceCallback(devices, Handler(Looper.getMainLooper()))
        ContextCompat.registerReceiver(
            context,
            volume,
            IntentFilter(VOLUME_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun release() {
        player.removeListener(this)
        audioManager.unregisterAudioDeviceCallback(devices)
        context.unregisterReceiver(volume)
    }

    /** Outputs of these `AudioDeviceInfo` types were just connected (several at once count once per event). */
    fun onOutputsAdded(types: List<Int>) = types.mapNotNull(::connectEvent).distinct().forEach(::on)

    /**
     * An output went away: the volume now read is another output's, so a pause by volume 0 on the old one is not
     * resumed by it.
     */
    fun onOutputsRemoved() {
        pausedByVolume = false
        volumeZero = isVolumeZero()
    }

    /** Every media button event, before Media3 handles it: remembers the media card's dismissal for [stopPauses]. */
    fun onMediaButton(intent: Intent) {
        dismissed = intent.getBooleanExtra(MediaNotification.NOTIFICATION_DISMISSED_EVENT_KEY, false)
    }

    fun on(event: AutoEvent) {
        val action = autoAction(event, settings(), state())
        // Never start music over a call: the focus held from before the call would let it play.
        if (action == AutoAction.Play && inCall()) return
        when (action) {
            AutoAction.Play -> {
                pausedByVolume = false
                if (!player.playWhenReady) expected = true
                // Prepares a stopped player and restarts an ended queue, like a Play button.
                Util.handlePlayButtonAction(player)
            }

            AutoAction.Pause -> {
                if (player.playWhenReady) expected = false
                player.pause()
                pausedByVolume = event == AutoEvent.VolumeZero
            }

            AutoAction.Nothing -> {
                Unit
            }
        }
    }

    /**
     * A controller's stop pauses instead. Not after the media card was swiped away ([onMediaButton]): that must
     * really stop, or the card comes back.
     */
    fun stopPauses(): Boolean {
        val dismissal = dismissed
        dismissed = false
        return !dismissal && autoAction(AutoEvent.RemoteStop, settings(), state()) == AutoAction.Pause
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        if (expected == playWhenReady) {
            expected = null
            return
        }
        expected = null
        // Anyone else's play or pause: the volume no longer has anything to resume.
        pausedByVolume = false
    }

    private fun state() = AutoState(player.wantsToPlay(), player.mediaItemCount > 0, pausedByVolume)

    private fun isVolumeZero() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0

    private fun inCall() =
        audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
}

/** The platform's volume broadcast (hidden in the SDK; Media3's StreamVolumeManager listens to it too). */
internal const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
