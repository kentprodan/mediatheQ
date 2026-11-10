package io.github.kentprodan.mediatheq.core.data

/**
 * Settings -> Playback -> Automatic actions (#141, ADR 0033): playback starts or pauses by itself on device events,
 * while the playback service is alive.
 */
data class AutoActionsPreferences(
    /** Media volume at 0 pauses; raised again, what that paused plays on. */
    val volumePause: Boolean = false,
    /** Wired or USB headphones connected: play. */
    val headphonePlay: Boolean = true,
    /** A Bluetooth headphone, speaker or car connected: play. */
    val bluetoothPlay: Boolean = false,
    /** Swiping the app away pauses (off: music keeps playing). */
    val pauseOnTaskRemoval: Boolean = false,
    /** A stop from outside (car, watch, headset) pauses instead, so the queue stays ready. */
    val ignoreRemoteStop: Boolean = true,
)
