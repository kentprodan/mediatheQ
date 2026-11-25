// Rules adapted from Moonfin (https://github.com/Moonfin-Client/Moonfin-Core, packages/moonfin_native_video:
// DecoderReclaimPolicy, ResumeWedgePolicy, VideoDecoderStartupFailure, UnsupportedVideoPolicy, the audio clock
// recovery in Media3VideoView and Media3LogRelay), GPL-3.0. See THIRD_PARTY.md.
package io.github.kentprodan.mediatheq.core.playback

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import io.github.kentprodan.mediatheq.core.model.MediaId

/** The recovery rules for a playing video (#632); pure, so a JVM test drives them. */
internal object VideoRecoveryRules {
    /** A decoder the platform took back (a long pause, the screensaver) is asked for again twice, no more. */
    const val MAX_RECLAIM_RETRIES = 2

    /** A resume still buffering this long after play was pressed... */
    const val WEDGE_CHECK_MS = 3_000L

    /** ...with this much loaded ahead is stuck, not waiting for the network (Fire TV seen). */
    const val WEDGE_RUNWAY_MS = 10_000L

    fun retryReclaimed(
        errorCode: Int,
        retries: Int,
    ): Boolean = errorCode == PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED && retries < MAX_RECLAIM_RETRIES

    fun wedged(
        buffering: Boolean,
        playWhenReady: Boolean,
        bufferedAheadMs: Long,
    ): Boolean = buffering && playWhenReady && bufferedAheadMs >= WEDGE_RUNWAY_MS

    /**
     * The video decoder failed before showing a frame: this device cannot play this stream, and a retry fails the same
     * way. A decoder reclaimed later is [retryReclaimed]'s.
     */
    fun decoderCannotStart(
        errorCode: Int,
        rendererMimeType: String?,
        firstFrameShown: Boolean,
    ): Boolean =
        !firstFrameShown &&
            rendererMimeType?.startsWith("video/") == true &&
            (
                errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
            )

    /**
     * A video item ready with no video selected: Media3 left the track unselected (Dolby Vision AV1, full-range PQ, a
     * profile no decoder takes) or the extractor could not read it (VC-1 in TS). Nothing throws; the sound plays over
     * black, so the selection is the only signal.
     */
    fun videoUnplayable(
        ready: Boolean,
        hasVideoTrack: Boolean,
        videoSelected: Boolean,
    ): Boolean = ready && !(hasVideoTrack && videoSelected)

    /**
     * Some passthrough HALs reset the AudioTrack head mid-stream; Media3 reads it as a 32-bit wrap and the audio clock
     * jumps hours ahead, so the picture chases a time that never comes. A head position past the player's own age by an
     * hour cannot be real.
     */
    const val CLOCK_MARGIN_MS = 3_600_000L

    /** One clock recovery a minute at most. */
    const val CLOCK_RECOVERY_EVERY_MS = 60_000L

    fun clockCorrupted(
        reportedPositionUs: Long,
        playerAgeMs: Long,
    ): Boolean = reportedPositionUs / 1_000 >= playerAgeMs + CLOCK_MARGIN_MS
}

/**
 * Keeps a video playing through what devices do to it (#632): a reclaimed decoder is prepared again in place, a stuck
 * resume too, and a video this device cannot show (a decoder that fails before its first frame, or no video selected)
 * is asked from the server as a conversion at once ([convert] is false once the item was converted, and then the error
 * stands). [reprepare] opens the current item again where it is, converted or not.
 */
@UnstableApi
internal class VideoRecovery(
    private val player: Player,
    private val handler: Handler,
    private val currentId: () -> MediaId?,
    private val isVideo: () -> Boolean,
    private val convert: (MediaId) -> Boolean,
    private val reprepare: () -> Unit,
    private val log: (String) -> Unit,
) : Player.Listener {
    // Per video, not per media item: a re-prepare replaces the item, which must not reset the counts.
    private var video: MediaId? = null
    private var reclaimRetries = 0
    private var firstFrameShown = false
    private val wedgeCheck = Runnable { checkWedge() }

    private fun follow() {
        val id = currentId()
        if (id != video) {
            video = id
            reclaimRetries = 0
            firstFrameShown = false
        }
    }

    /** Handles [error] if a recovery covers it: `true` = nothing to report. */
    fun recover(error: PlaybackException): Boolean {
        follow()
        val id = video ?: return false
        if (!isVideo()) return false
        if (VideoRecoveryRules.retryReclaimed(error.errorCode, reclaimRetries)) {
            reclaimRetries++
            log("decoder reclaimed, prepared again ($reclaimRetries)")
            reprepare()
            return true
        }
        val mime = (error as? ExoPlaybackException)?.rendererFormat?.sampleMimeType
        if (VideoRecoveryRules.decoderCannotStart(error.errorCode, mime, firstFrameShown) && convert(id)) {
            log("$mime decoder cannot start (${error.errorCodeName}), converting on the server")
            reprepare()
            return true
        }
        return false
    }

    override fun onRenderedFirstFrame() {
        follow()
        firstFrameShown = true
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) checkVideo(player.currentTracks)
    }

    override fun onTracksChanged(tracks: Tracks) {
        if (player.playbackState == Player.STATE_READY) checkVideo(tracks)
    }

    private fun checkVideo(tracks: Tracks) {
        follow()
        val id = video ?: return
        val unplayable =
            VideoRecoveryRules.videoUnplayable(
                ready = true,
                hasVideoTrack = tracks.containsType(C.TRACK_TYPE_VIDEO),
                videoSelected = tracks.isTypeSelected(C.TRACK_TYPE_VIDEO),
            )
        if (isVideo() && unplayable && convert(id)) {
            log("no video track selected, converting on the server")
            reprepare()
        }
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        handler.removeCallbacks(wedgeCheck)
        if (playWhenReady && isVideo()) handler.postDelayed(wedgeCheck, VideoRecoveryRules.WEDGE_CHECK_MS)
    }

    private fun checkWedge() {
        val ahead = player.bufferedPosition - player.currentPosition
        if (isVideo() &&
            VideoRecoveryRules.wedged(player.playbackState == Player.STATE_BUFFERING, player.playWhenReady, ahead)
        ) {
            log("resume stuck buffering with ${ahead / 1_000} s loaded, prepared again")
            reprepare()
        }
    }

    fun release() {
        handler.removeCallbacks(wedgeCheck)
    }
}

/**
 * Media3's log, passed on unchanged, read for the "Spurious audio timestamp" warning whose head position tells a
 * corrupted audio clock ([VideoRecoveryRules.clockCorrupted]); [onHeadPositionUs] gets it on the playback thread.
 */
@UnstableApi
internal class AudioClockWatch(
    private val onHeadPositionUs: (Long) -> Unit,
) : Log.Logger {
    override fun d(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = Log.Logger.DEFAULT.d(tag, message, throwable)

    override fun i(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = Log.Logger.DEFAULT.i(tag, message, throwable)

    override fun w(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        spuriousHeadPositionUs(message)?.let(onHeadPositionUs)
        Log.Logger.DEFAULT.w(tag, message, throwable)
    }

    override fun e(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = Log.Logger.DEFAULT.e(tag, message, throwable)
}

/** The playback head position (µs, the fourth value) of DefaultAudioSink's frame position mismatch warning. */
internal fun spuriousHeadPositionUs(message: String): Long? =
    message
        .takeIf { it.startsWith(SPURIOUS_TIMESTAMP) }
        ?.removePrefix(SPURIOUS_TIMESTAMP)
        ?.split(", ")
        ?.getOrNull(3)
        ?.trim()
        ?.toLongOrNull()

private const val SPURIOUS_TIMESTAMP = "Spurious audio timestamp (frame position mismatch): "
