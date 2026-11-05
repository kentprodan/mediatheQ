package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Remembers where each album was left for its Resume button (#132): [record] gets the playing track and position on
 * pause, every [interval] while playing, when a track is left (where it ended) and at the end of the queue. Works from
 * the media id alone, so downloads and streams count the same. [scope] runs on the player's thread.
 */
@UnstableApi // PositionInfo.mediaItem: the left track, also when it was removed from the queue.
internal class ResumeListener(
    private val player: Player,
    private val record: (MediaId, Duration) -> Unit,
    private val scope: CoroutineScope,
    private val interval: Duration = 10.seconds,
) : Player.Listener {
    private var ticker: Job? = null

    private fun MediaItem?.trackId(): MediaId? = this?.mediaId?.let { runCatching { MediaId.parse(it) }.getOrNull() }

    private fun recordCurrent() {
        player.currentMediaItem.trackId()?.let { record(it, player.currentPosition.milliseconds) }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        ticker?.cancel()
        if (isPlaying) {
            ticker =
                scope.launch {
                    while (isActive) {
                        delay(interval)
                        recordCurrent()
                    }
                }
        } else if (player.playbackState == Player.STATE_READY) {
            recordCurrent()
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        val left =
            reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
                oldPosition.mediaItemIndex != newPosition.mediaItemIndex ||
                oldPosition.periodUid != newPosition.periodUid
        // The old position is where the left track ended: its end for a track played through.
        if (left) oldPosition.mediaItem.trackId()?.let { record(it, oldPosition.positionMs.milliseconds) }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) recordCurrent()
    }
}
