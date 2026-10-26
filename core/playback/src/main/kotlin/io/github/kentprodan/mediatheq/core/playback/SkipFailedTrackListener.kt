package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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
        val causes = generateSequence<Throwable>(error) { it.cause }
        val offline = causes.any { it is OfflineException }
        val trackAtFault = offline || causes.any { it is StreamUnavailableException && it.error.isTrackOnly() }
        val skip = trackAtFault && player.hasNextMediaItem() && ++failuresInARow < player.mediaItemCount
        val title =
            player.currentMediaItem
                ?.mediaMetadata
                ?.title
                ?.toString()
                .orEmpty()
        val reason = if (offline) PlaybackError.Reason.NotDownloaded else PlaybackError.Reason.Failed
        onError(PlaybackError(title, reason, skip))
        if (skip) {
            player.seekToNextMediaItem()
            player.prepare()
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
