package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import io.github.kentprodan.mediatheq.core.common.AppLog

/**
 * What the player does, into the app's log (#169): each track with its title and id, play and pause with how the
 * stream is delivered ([stream], e.g. "DirectPlay · Original"), the end of the queue, and errors with their causes.
 */
internal class PlaybackLogListener(
    private val player: Player,
    private val log: AppLog,
    private val stream: () -> String?,
) : Player.Listener {
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        mediaItem ?: return
        log.info(TAG, "Track ${mediaItem.describe()} (${REASONS[reason] ?: reason})")
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            val how = stream()?.let { " · $it" }.orEmpty()
            log.info(TAG, "Playing ${player.currentMediaItem?.describe()}$how")
        } else if (player.playbackState == Player.STATE_READY) {
            log.info(TAG, "Paused")
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_ENDED -> log.info(TAG, "Queue ended")
            Player.STATE_IDLE -> log.info(TAG, "Stopped")
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        log.warn(TAG, "Error ${error.errorCodeName} on ${player.currentMediaItem?.describe()}", error)
    }

    private fun MediaItem.describe() = "\"${mediaMetadata.title ?: ""}\" [$mediaId]"

    private companion object {
        const val TAG = "Playback"
        val REASONS =
            mapOf(
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO to "next",
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK to "chosen",
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT to "repeat",
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED to "new queue",
            )
    }
}
