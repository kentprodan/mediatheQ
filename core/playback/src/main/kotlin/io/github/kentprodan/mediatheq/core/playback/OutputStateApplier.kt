package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Applies each [AudioOutputState] to the player (#165, the service wiring of #65): offload only when it changes (a
 * change selects tracks again and drops the gapless next item), the volume again (bit-perfect plays at full volume),
 * the equalizer only in [AudioMode.Processed], the state for screens ([extras]) and the output device for its
 * profile ([device], last: its settings decide again and come back here). Main thread only.
 */
@UnstableApi
internal class OutputStateApplier(
    private val player: Player,
    private val state: () -> AudioOutputState,
    /** Sets the current track's volume ([NormalizationListener.apply]). */
    private val volume: () -> Unit,
    /** Turns the equalizer on the player's audio session on or off. */
    private val equalizer: (audioSessionId: Int, on: Boolean) -> Unit,
    private val extras: (AudioOutputState) -> Unit,
    private val device: (OutputDevice) -> Unit,
) {
    private var offload: Boolean? = null

    fun apply(next: AudioOutputState) {
        applyOffload(next.offload)
        volume()
        applyEqualizer()
        extras(next)
        device(next.output)
    }

    fun applyOffload(allowed: Boolean) {
        if (allowed == offload) return
        player.applyOffload(allowed)
        offload = allowed
    }

    fun applyEqualizer() = equalizer(player.audioSessionId, state().mode == AudioMode.Processed)

    /**
     * The output refused the AudioTrack: while bit-perfect was preferred, [outputFailed] drops the preference and
     * playback goes on normally; a passthrough track is tried once more (ADR 0039). Either way it is prepared again.
     * `false` for any other error, and for a passthrough track that failed twice: then the error shows.
     */
    fun recover(
        error: PlaybackException,
        outputFailed: () -> Boolean,
    ): Boolean = (error.isAudioOutputError() && outputFailed()).also { if (it) player.prepare() }
}
