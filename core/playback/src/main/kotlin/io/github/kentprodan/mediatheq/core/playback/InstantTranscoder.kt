package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlin.random.Random

/**
 * Settings -> Playback -> Decoding and transcoding -> Force instant transcoding (#138): when the network turns
 * metered (Wi-Fi lost) the playing track starts again at the same position in the metered quality, instead of from
 * the next track. Only for a track streaming in another quality; a download or a fully cached original goes on.
 * The new stream is a new play session, reported as such.
 */
@UnstableApi
internal class InstantTranscoder(
    private val player: ExoPlayer,
    private val enabled: suspend () -> Boolean,
    /** No network at all (a dead zone, a roam between Wi-Fi networks) is no reason to restart. */
    private val isOnline: () -> Boolean,
    /** Whether [MediaId] streams in another quality than the metered one and would stream again (not local). */
    private val needsNewStream: suspend (MediaId) -> Boolean,
) {
    private var wasMetered: Boolean? = null

    /** Every network change, on the player's thread. */
    suspend fun onNetwork(metered: Boolean) {
        // Offline the buffer plays on; the change counts once a network carries traffic again.
        if (!isOnline()) return
        val before = wasMetered
        wasMetered = metered
        if (before != false || !metered || !player.wantsToPlay() || !enabled()) return
        val item = player.currentMediaItem ?: return
        val id = item.queuedTrackId() ?: return
        if (!needsNewStream(id) || player.currentMediaItem?.entryId() != item.entryId()) return
        restartCurrent()
    }

    /** A fresh source for the current entry (it chooses its stream again), same place, same shuffle order. */
    private fun restartCurrent() {
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        val order = player.playOrder(shuffled = true)
        // An equal item still gets a new source: ours cannot update in place (CompositeMediaSource).
        player.replaceMediaItem(index, player.getMediaItemAt(index).buildUpon().build())
        // Replacing drops the entry at a random place of the shuffle order; it keeps its own.
        player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
        player.seekTo(index, position)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
    }
}

/** A streaming track whose known stream has another quality than [wanted] gets a new one; local ones never do. */
internal fun wantsNewStream(
    known: StreamingQuality?,
    wanted: StreamingQuality,
    local: Boolean,
): Boolean = !local && known != null && known != wanted
