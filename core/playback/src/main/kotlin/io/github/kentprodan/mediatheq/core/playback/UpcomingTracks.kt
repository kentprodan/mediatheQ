package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.github.kentprodan.mediatheq.core.model.MediaId

/** The current item's track, or `null` without one (or with an item that is no track). */
internal fun Player.currentTrackId(): MediaId? =
    currentMediaItem?.mediaId?.let {
        runCatching { MediaId.parse(it) }.getOrNull()
    }

/**
 * The next [count] tracks in play order (shuffle and repeat included), never the current one again and none [skip]
 * rejects. Repeat one counts as off: the queue's real successors are worth more than the same track. Player thread only.
 */
internal fun Player.upcomingTracks(
    count: Int,
    skip: (MediaId) -> Boolean = { false },
): List<MediaId> = upcomingItems(count, skip).map { it.first }

/** [upcomingTracks] with each track's queue item. Player thread only. */
internal fun Player.upcomingItems(
    count: Int,
    skip: (MediaId) -> Boolean = { false },
): List<Pair<MediaId, MediaItem>> {
    val timeline = currentTimeline
    if (timeline.isEmpty) return emptyList()
    val current = currentMediaItem?.mediaId
    val repeat = if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else repeatMode
    val result = mutableListOf<Pair<MediaId, MediaItem>>()
    var index = currentMediaItemIndex
    repeat(timeline.windowCount) {
        if (result.size == count) return result
        index = timeline.getNextWindowIndex(index, repeat, shuffleModeEnabled)
        if (index == C.INDEX_UNSET) return result
        val item = getMediaItemAt(index)
        val id = item.queuedTrackId()
        if (id != null && item.mediaId != current && result.none { it.first == id } && !skip(id)) result += id to item
    }
    return result
}
