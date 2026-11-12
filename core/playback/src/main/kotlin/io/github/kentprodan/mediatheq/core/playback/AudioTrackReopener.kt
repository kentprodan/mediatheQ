package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Makes the player open a new AudioTrack without pausing (#165): an AudioTrack already open when bit-perfect is
 * preferred stays on the normal mixer, and only one opened after it, with none of ours open, reaches the bit-perfect
 * output. Audio is turned off (the audio renderer is disabled, so its sink releases the track) until the old track is
 * gone ([onTracksReleased]) or [timeoutMs] passed, then on again. Playback keeps going meanwhile: a few tens of
 * milliseconds of sound are skipped, but nothing sees a pause (focus, server reports, notification). Main thread only.
 */
internal class AudioTrackReopener(
    private val player: Player,
    private val schedule: (Long, () -> Unit) -> () -> Unit,
    private val timeoutMs: Long = TIMEOUT_MS,
) {
    private var cancelTimeout: (() -> Unit)? = null

    val reopening: Boolean get() = cancelTimeout != null

    fun reopen() {
        if (reopening) return
        setAudio(enabled = false)
        cancelTimeout = schedule(timeoutMs, ::finish)
    }

    /** No AudioTrack of the player is open any more. */
    fun onTracksReleased() {
        if (reopening) finish()
    }

    fun release() {
        if (reopening) finish()
    }

    private fun finish() {
        cancelTimeout?.invoke()
        cancelTimeout = null
        setAudio(enabled = true)
    }

    private fun setAudio(enabled: Boolean) {
        // Built on the current parameters: the offload preference set just before must stay.
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !enabled)
                .build()
    }

    private companion object {
        /** A track that is never released (the device keeps it) must not keep the music silent. */
        const val TIMEOUT_MS = 1_000L
    }
}
