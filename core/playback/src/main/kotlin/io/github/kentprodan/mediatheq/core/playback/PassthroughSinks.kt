package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * The player's renderers (ADR 0054, 0055): the stock ones, with the stock `DefaultAudioSink` wrapped in the passthrough
 * recovery (#377) and the output's passthrough policy.
 */
@UnstableApi
internal class PassthroughRenderersFactory(
    context: Context,
    private val allowedPassthrough: () -> Set<String>,
    private val onRecoveryNeeded: (reason: String) -> Unit,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink? {
        val sink = super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams) ?: return null
        val recovering =
            PassthroughRecoveryAudioSink(sink, PassthroughSilenceRecovery(), RouteFlapHold(), onRecoveryNeeded)
        return PassthroughPolicyAudioSink(recovering, allowedPassthrough)
    }
}

/** The compressed formats an HDMI route can take untouched (a bitstream), as opposed to PCM or offloaded music. */
internal val BITSTREAM_MIME_TYPES =
    setOf(
        MimeTypes.AUDIO_AC3,
        MimeTypes.AUDIO_E_AC3,
        MimeTypes.AUDIO_E_AC3_JOC,
        MimeTypes.AUDIO_AC4,
        MimeTypes.AUDIO_TRUEHD,
        MimeTypes.AUDIO_DTS,
        MimeTypes.AUDIO_DTS_HD,
        MimeTypes.AUDIO_DTS_EXPRESS,
    )

/**
 * Whether a bitstream of [mime] may leave untouched (#377, ADR 0055): only what [allowed] (the route's passthrough set
 * while the output's switch is on, `AudioOutputState.requestPassthrough`) names. E-AC-3 JOC may still go as plain
 * E-AC-3 where only that is allowed, Media3's fallback (ADR 0039). Anything that is not a bitstream is not ours to
 * judge. Pure.
 */
internal fun passthroughAllowed(
    mime: String?,
    allowed: Set<String>,
): Boolean =
    mime !in BITSTREAM_MIME_TYPES ||
        mime in allowed ||
        (mime == MimeTypes.AUDIO_E_AC3_JOC && MimeTypes.AUDIO_E_AC3 in allowed)

/**
 * The output's passthrough switch made real (#377, ADR 0055): a bitstream the policy does not allow is unsupported
 * here, so the renderer decodes it (MediaCodec, then FFmpeg) instead of passing it through. It only ever narrows what
 * the sink below says, never forces a bitstream onto a route that cannot carry it. The offload answer is vetoed too,
 * since the renderer asks it first.
 */
@UnstableApi
internal class PassthroughPolicyAudioSink(
    delegate: AudioSink,
    private val allowed: () -> Set<String>,
) : ForwardingAudioSink(delegate) {
    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatSupport(format: Format): Int =
        if (vetoed(format)) AudioSink.SINK_FORMAT_UNSUPPORTED else super.getFormatSupport(format)

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
        if (vetoed(format)) AudioOffloadSupport.DEFAULT_UNSUPPORTED else super.getFormatOffloadSupport(format)

    private fun vetoed(format: Format) = !passthroughAllowed(format.sampleMimeType, allowed())
}

// The class below is adapted from Moonfin's PassthroughRecoveryAudioSink (github.com/Moonfin-Client/Moonfin-Core,
// packages/moonfin_native_video, GPL-3.0; see THIRD_PARTY.md): Media3 1.11's configure, our bitstream set.

/**
 * Watches the sink for a bitstream track that went silent and asks the player to rebuild it, and holds a bitstream
 * track through an HDMI route flap so the player never hands it to a decoder (#377). The decisions live in
 * [PassthroughSilenceRecovery] and [RouteFlapHold]; this wrapper only feeds them what the renderer already does, and
 * until a dead track has been proven the sink behaves byte for byte like the one it wraps.
 *
 * A dead track is flushed, which releases the output and keeps the configuration, and the first write after the route
 * returns rebuilds the same passthrough output. The platform pins the sink's capabilities to plain stereo on a dead
 * track until the next device event, so the delegate's own answer for the input format says when the route is back.
 *
 * [onRecoveryNeeded] fires on the playback thread and must hand off to the player's own thread itself.
 */
@UnstableApi
internal class PassthroughRecoveryAudioSink(
    delegate: AudioSink,
    private val recovery: PassthroughSilenceRecovery,
    private val flap: RouteFlapHold,
    private val onRecoveryNeeded: (reason: String) -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ForwardingAudioSink(delegate) {
    private var inputFormat: Format? = null
    private var pendingWriteError: AudioSink.WriteException? = null
    private var playbackHandler: Handler? = null
    private var deferredNotify: Runnable? = null

    override fun setListener(listener: AudioSink.Listener) {
        super.setListener(FilteringListener(listener))
    }

    override fun configure(config: AudioSink.AudioSinkConfig) {
        val format = config.format
        inputFormat = format
        // The delegate reports capability changes on the thread it was configured from, so a deferral lands there.
        if (playbackHandler == null) playbackHandler = Looper.myLooper()?.let { Handler(it) }
        // Only a real bitstream counts for the capability deferral: offloaded music rides through the sink compressed
        // too, and a dead offload track has to keep reaching the player's own offload fallback. Decoded PCM opts into
        // the rebuild anyway: it dies in a route renegotiation just like a bitstream track.
        val bitstream = format.sampleMimeType in BITSTREAM_MIME_TYPES
        val decodedPcm = format.sampleMimeType == MimeTypes.AUDIO_RAW
        flap.onConfigure(bitstream = bitstream, rebuildOnDeadTrack = bitstream || decodedPcm)
        recovery.onConfigure(passthrough = bitstream, nowMs = clock())
        super.configure(config)
    }

    override fun play() {
        if (recovery.onPlay(clock())) onRecoveryNeeded("resume on a route that already lost one track")
        super.play()
    }

    override fun pause() {
        recovery.onPause()
        super.pause()
    }

    override fun flush() {
        recovery.onFlush(clock())
        super.flush()
    }

    override fun reset() {
        recovery.onReset(clock())
        flap.onReset()
        pendingWriteError = null
        cancelDeferredNotify()
        super.reset()
    }

    override fun release() {
        cancelDeferredNotify()
        super.release()
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        // Refusing the write is ordinary backpressure; only right after a recovery flush, so the old track is released
        // before the next write opens its replacement.
        if (recovery.shouldHoldWrites(clock())) return false
        val decision = flap.writeDecision(inputSupportedByDelegate(), clock())
        when (decision) {
            RouteFlapHold.Write.HOLD -> {
                return false
            }

            RouteFlapHold.Write.RETHROW -> {
                // The route never came back: the player gets the error it would have had and recovers as before.
                pendingWriteError?.let {
                    pendingWriteError = null
                    throw it
                }
            }

            RouteFlapHold.Write.WRITE, RouteFlapHold.Write.REBUILD -> {
                Unit
            }
        }
        return try {
            val handled = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
            if (decision == RouteFlapHold.Write.REBUILD && handled) {
                flap.onRebuilt()
                pendingWriteError = null
            }
            handled
        } catch (e: AudioSink.InitializationException) {
            if (decision != RouteFlapHold.Write.REBUILD) throw e
            // The route says it is back but the link still settles: the flush clears the retry deadline.
            flush()
            false
        } catch (e: AudioSink.WriteException) {
            if (!RouteFlapHold.isDeadObjectCode(e.errorCode) || !flap.onDeadTrack(clock())) throw e
            // Release the dead output, keep the configuration: the write after the route returns rebuilds the track.
            pendingWriteError = e
            flush()
            false
        }
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val positionUs = super.getCurrentPositionUs(sourceEnded)
        if (!sourceEnded && recovery.onPosition(positionUs, hasPendingData(), clock())) {
            onRecoveryNeeded("position frozen with data waiting")
        }
        return positionUs
    }

    /** The delegate's live answer, below any policy veto layered above. */
    private fun inputSupportedByDelegate(): Boolean = inputFormat?.let { super.supportsFormat(it) } ?: false

    private fun onCapabilitiesChanged(listener: AudioSink.Listener) {
        when (flap.onCapabilitiesChanged(inputSupportedByDelegate(), clock())) {
            RouteFlapHold.Notify.FORWARD -> {
                cancelDeferredNotify()
                listener.onAudioCapabilitiesChanged()
            }

            RouteFlapHold.Notify.SWALLOW -> {
                cancelDeferredNotify()
            }

            RouteFlapHold.Notify.DEFER -> {
                scheduleDeferredNotify(listener)
            }
        }
    }

    private fun scheduleDeferredNotify(listener: AudioSink.Listener) {
        if (deferredNotify != null) return
        val handler = playbackHandler
        if (handler == null) {
            flap.onDeferredNotifyDue()
            listener.onAudioCapabilitiesChanged()
            return
        }
        val runnable =
            Runnable {
                deferredNotify = null
                if (flap.onDeferredNotifyDue()) listener.onAudioCapabilitiesChanged()
            }
        deferredNotify = runnable
        handler.postDelayed(runnable, flap.maxHoldMs)
    }

    private fun cancelDeferredNotify() {
        deferredNotify?.let { playbackHandler?.removeCallbacks(it) }
        deferredNotify = null
    }

    /** Forwards everything to the renderer's listener, except a capabilities change, which goes through the hold. */
    private inner class FilteringListener(
        private val inner: AudioSink.Listener,
    ) : AudioSink.Listener {
        override fun onPositionDiscontinuity() = inner.onPositionDiscontinuity()

        override fun onPositionAdvancing(playoutStartSystemTimeMs: Long) =
            inner.onPositionAdvancing(playoutStartSystemTimeMs)

        override fun onUnderrun(
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long,
        ) = inner.onUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs)

        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) =
            inner.onSkipSilenceEnabledChanged(skipSilenceEnabled)

        override fun onOffloadBufferEmptying() = inner.onOffloadBufferEmptying()

        override fun onOffloadBufferFull() = inner.onOffloadBufferFull()

        override fun onAudioSinkError(audioSinkError: Exception) = inner.onAudioSinkError(audioSinkError)

        override fun onAudioCapabilitiesChanged() = onCapabilitiesChanged(inner)

        override fun onAudioTrackInitialized(audioTrackConfig: AudioSink.AudioTrackConfig) =
            inner.onAudioTrackInitialized(audioTrackConfig)

        override fun onAudioTrackReleased(audioTrackConfig: AudioSink.AudioTrackConfig) =
            inner.onAudioTrackReleased(audioTrackConfig)

        override fun onSilenceSkipped() = inner.onSilenceSkipped()

        override fun onAudioSessionIdChanged(audioSessionId: Int) = inner.onAudioSessionIdChanged(audioSessionId)
    }
}
