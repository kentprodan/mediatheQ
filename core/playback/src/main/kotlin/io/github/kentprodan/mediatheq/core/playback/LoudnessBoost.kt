package io.github.kentprodan.mediatheq.core.playback

import android.media.audiofx.LoudnessEnhancer
import io.github.kentprodan.mediatheq.core.common.AppLog
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Raises a quiet track by its positive normalization gain (#627): the platform's `LoudnessEnhancer` on the player's
 * audio session, whose limiter keeps the result from clipping. Every call goes to one background thread: the audio
 * server can stall for seconds while it opens a passthrough track (engine research 3.2), and the main thread must never
 * wait on it. Only on PCM: the policy turns offload off for a raised track.
 */
internal class LoudnessBoost(
    private val log: AppLog,
    private val executor: Executor = Executors.newSingleThreadExecutor { Thread(it, "MqLoudness") },
) {
    private var enhancer: LoudnessEnhancer? = null
    private var millibels = 0

    /** A new audio session (each AudioTrack the player opens may have one): the effect moves to it. */
    fun attach(sessionId: Int) =
        executor.execute {
            enhancer?.release()
            enhancer =
                runCatching { LoudnessEnhancer(sessionId) }
                    .onFailure { log.info(TAG, "LoudnessEnhancer unavailable: ${it.message}") }
                    .getOrNull()
            apply()
        }

    /** The boost for the current track, in millibels; 0 switches the effect off. */
    fun set(millibels: Int) =
        executor.execute {
            if (millibels == this.millibels) return@execute
            this.millibels = millibels
            apply()
        }

    fun release() =
        executor.execute {
            enhancer?.release()
            enhancer = null
        }

    private fun apply() {
        val effect = enhancer ?: return
        runCatching {
            effect.setTargetGain(millibels)
            effect.enabled = millibels > 0
        }.onFailure { log.info(TAG, "LoudnessEnhancer refused $millibels mB: ${it.message}") }
    }
}

private const val TAG = "MqAudioOutput"
