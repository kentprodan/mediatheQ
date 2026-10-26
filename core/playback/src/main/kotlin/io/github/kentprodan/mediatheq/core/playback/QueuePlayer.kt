package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlin.random.Random

/**
 * The player the session exposes: while shuffle is on, entries added through "play next" play right after the current
 * one and "add to queue" entries play last (see [shuffleWithInserted]), instead of landing at random places. Shuffle
 * turned on mid-queue goes on with the playing track and plays all the others after it (see [shuffleFrom]).
 */
@UnstableApi
internal class QueuePlayer(
    private val player: ExoPlayer,
) : ForwardingPlayer(player) {
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        // Before the change, so its event already sees the new order. (Album Shuffle sets the mode before its new
        // queue, which then gets an order of its own: reshuffling the old one here costs nothing.)
        if (shuffleModeEnabled && !this.shuffleModeEnabled && mediaItemCount > 0) {
            val order = shuffleFrom(currentMediaItemIndex, mediaItemCount, Random)
            player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
        }
        super.setShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun addMediaItem(mediaItem: MediaItem) = addMediaItems(mediaItemCount, listOf(mediaItem))

    override fun addMediaItem(
        index: Int,
        mediaItem: MediaItem,
    ) = addMediaItems(index, listOf(mediaItem))

    override fun addMediaItems(mediaItems: List<MediaItem>) = addMediaItems(mediaItemCount, mediaItems)

    override fun addMediaItems(
        index: Int,
        mediaItems: List<MediaItem>,
    ) {
        val at = index.coerceAtMost(mediaItemCount)
        val appended = at == mediaItemCount
        super.addMediaItems(at, mediaItems)
        if (!shuffleModeEnabled || mediaItems.isEmpty()) return
        val order = shuffleWithInserted(playOrder(), at until at + mediaItems.size, currentMediaItemIndex, appended)
        player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
    }
}
