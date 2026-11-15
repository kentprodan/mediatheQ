package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Auto-play (#275): when an episode becomes the playing item with nothing after it, its next episode is queued behind
 * it, so the player goes on without a gap; the countdown's Cancel takes it off again. Only on an item change, never on a
 * queue change, so a cancelled episode is not queued again. [scope] runs on the player's thread.
 */
internal class NextEpisodeQueuer(
    private val player: Player,
    private val scope: CoroutineScope,
    private val videos: VideoRepository,
    private val toItem: suspend (Video) -> MediaItem,
    /** Settings -> Video -> Auto-play next episode (#276). */
    private val enabled: suspend () -> Boolean = { true },
) : Player.Listener {
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        val item = mediaItem?.takeIf { it.isVideo() } ?: return
        if (player.hasNextMediaItem()) return
        val id = runCatching { MediaId.parse(item.mediaId) }.getOrNull() ?: return
        scope.launch {
            if (!enabled()) return@launch
            val next = videos.nextEpisode(id) ?: return@launch
            // Still the same item with nothing after it: the user may have moved on meanwhile.
            if (player.currentMediaItem?.mediaId == item.mediaId && !player.hasNextMediaItem()) {
                player.addMediaItem(toItem(next))
            }
        }
    }
}
