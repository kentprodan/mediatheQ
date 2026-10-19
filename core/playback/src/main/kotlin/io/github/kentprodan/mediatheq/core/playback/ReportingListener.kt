package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlin.time.Duration.Companion.milliseconds

/** Turns player events into server reports (runs on the player's thread). */
internal class ReportingListener(
    private val player: Player,
    private val reporter: PlaybackReporter,
) : Player.Listener {
    private fun currentId(): MediaId? =
        player.currentMediaItem?.mediaId?.let {
            runCatching { MediaId.parse(it) }.getOrNull()
        }

    private fun position() = player.currentPosition.milliseconds

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val itemId = currentId() ?: return
        if (isPlaying) {
            reporter.playing(itemId, ::position)
        } else if (player.playbackState == Player.STATE_READY) {
            reporter.paused(position())
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        // A new play session whenever the playing entry changes - also for the same track again (repeat one, the same
        // track twice in a row, a new queue starting with it) - so ids are not compared.
        val entryChanged =
            reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
                reason == Player.DISCONTINUITY_REASON_REMOVE ||
                oldPosition.mediaItemIndex != newPosition.mediaItemIndex ||
                oldPosition.periodUid != newPosition.periodUid
        if (entryChanged) {
            // currentPosition already belongs to the new entry; the old one ended where it was.
            reporter.ended(oldPosition.positionMs.milliseconds)
            // A gapless move to a buffered track does not toggle isPlaying, so the new session starts here.
            val itemId = currentId()
            if (player.isPlaying && itemId != null) reporter.playing(itemId, ::position)
        } else if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            reporter.seeked(newPosition.positionMs.milliseconds, player.isPlaying)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) reporter.ended(position())
    }

    override fun onPlayerError(error: PlaybackException) {
        reporter.ended(position(), failed = true)
    }
}
