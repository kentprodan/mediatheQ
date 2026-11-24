package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import io.github.kentprodan.mediatheq.core.common.MqError

/**
 * Reports every player error as a [PlaybackError] and, when only that track is at fault (not downloaded while offline,
 * refused or unknown to the server), goes on with the next one (#61). Other errors (a dropped connection, a server
 * down, signed out) stop where they are: skipping would run through the whole queue. A queue where nothing loads stops
 * after one lap (repeat all); a new queue starts a new lap.
 */
internal class SkipFailedTrackListener(
    private val player: Player,
    /** Handles an error that playback recovers from on its own (bit-perfect falling back, #65): nothing to report. */
    private val recover: (PlaybackException) -> Boolean = { false },
    private val onError: (PlaybackError) -> Unit,
) : Player.Listener {
    private var failuresInARow = 0

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) failuresInARow = 0
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) failuresInARow = 0
    }

    override fun onPlayerError(error: PlaybackException) {
        if (recover(error)) return
        val causes = generateSequence<Throwable>(error) { it.cause }
        val offline = causes.any { it is OfflineException }
        val trackAtFault = offline || causes.any { it is StreamUnavailableException && it.error.isTrackOnly() }
        fail(if (offline) PlaybackError.Reason.NotDownloaded else PlaybackError.Reason.Failed, trackAtFault)
    }

    /**
     * A track whose audio no decoder takes (#625): Media3 raises no error and runs a silent clock through it, so it is
     * the track's own failure. Never for a video, whose picture still plays.
     */
    override fun onTracksChanged(tracks: Tracks) {
        val undecodable =
            tracks.containsType(C.TRACK_TYPE_AUDIO) &&
                !tracks.containsType(C.TRACK_TYPE_VIDEO) &&
                !tracks.isTypeSupported(C.TRACK_TYPE_AUDIO, true)
        if (undecodable) fail(PlaybackError.Reason.Failed, trackAtFault = true, stop = true)
    }

    private fun fail(
        reason: PlaybackError.Reason,
        trackAtFault: Boolean,
        stop: Boolean = false,
    ) {
        val skip = trackAtFault && player.hasNextMediaItem() && ++failuresInARow < player.mediaItemCount
        val title =
            player.currentMediaItem
                ?.mediaMetadata
                ?.title
                ?.toString()
                .orEmpty()
        onError(PlaybackError(title, reason, skip))
        if (skip) {
            player.seekToNextMediaItem()
            player.prepare()
        } else if (stop) {
            player.pause()
        }
    }
}

// The server answered for this track alone; the same rule as the retries in StreamLoadErrorPolicy, minus signed out.
private fun MqError.isTrackOnly(): Boolean =
    this == MqError.Forbidden || this == MqError.NotFound || this is MqError.Client

/**
 * Tells the app's own screens (controllers of [packageName]) about a track that could not play. Only them: the media
 * notification and cars would show it as their own error.
 */
@UnstableApi
internal fun MediaSession.sendPlaybackError(
    packageName: String,
    error: PlaybackError,
) {
    val message = SessionError(SessionError.ERROR_IO, "A track could not play", error.toBundle())
    connectedControllers
        .filter { it.packageName == packageName && !isMediaNotificationController(it) }
        .forEach { sendError(it, message) }
}
