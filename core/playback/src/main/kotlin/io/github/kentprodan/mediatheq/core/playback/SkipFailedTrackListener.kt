package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * Reports every player error as a [PlaybackError] and, when only that track is at fault (not downloaded while offline,
 * no stream from the server), goes on with the next one (#61). Other errors (a dropped connection) stop where they
 * are: skipping would run through the whole queue. A queue where nothing loads stops after one lap (repeat all).
 */
internal class SkipFailedTrackListener(
    private val player: Player,
    private val onError: (PlaybackError) -> Unit,
) : Player.Listener {
    private var failuresInARow = 0

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) failuresInARow = 0
    }

    override fun onPlayerError(error: PlaybackException) {
        val causes = generateSequence<Throwable>(error) { it.cause }
        val offline = causes.any { it is OfflineException }
        val trackAtFault = offline || causes.any { it is StreamUnavailableException }
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
