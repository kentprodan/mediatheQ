package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Replaces the queue and starts playing (PlaybackConnection.play): at [startIndex], or, with [shuffle], at the first
 * track of the player's fresh shuffle order (so shuffled albums do not always start with track 1). Through a
 * MediaController the start must be left to the player: the controller's own view of the queue is unshuffled.
 */
internal fun Player.playQueue(
    items: List<MediaItem>,
    startIndex: Int,
    shuffle: Boolean,
) {
    shuffleModeEnabled = shuffle
    if (shuffle) {
        setMediaItems(items, true) // resetPosition: start where the shuffle order starts
    } else {
        setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0)
    }
    prepare()
    play()
}

/** Queues [items] right after the current entry; with nothing queued, plays them. */
internal fun Player.playNext(items: List<MediaItem>) {
    if (mediaItemCount == 0) playQueue(items, 0, shuffle = false) else addMediaItems(currentMediaItemIndex + 1, items)
}

/** Queues [items] at the end; with nothing queued, plays them. */
internal fun Player.addToQueue(items: List<MediaItem>) {
    if (mediaItemCount == 0) playQueue(items, 0, shuffle = false) else addMediaItems(items)
}

/** Moves the entry to queue index [to]; nothing when the entry is gone (the queue changed meanwhile). */
internal fun Player.moveEntry(
    entryId: String,
    to: Int,
) {
    val from = indexOfEntry(entryId).takeIf { it >= 0 } ?: return
    moveMediaItem(from, to.coerceIn(0, mediaItemCount - 1))
}

internal fun Player.removeEntry(entryId: String) {
    indexOfEntry(entryId).takeIf { it >= 0 }?.let(::removeMediaItem)
}

/** Plays the entry from its start. */
internal fun Player.skipToEntry(entryId: String) {
    val index = indexOfEntry(entryId).takeIf { it >= 0 } ?: return
    seekToDefaultPosition(index)
    prepare()
    play()
}

internal fun Player.indexOfEntry(entryId: String): Int =
    (0 until mediaItemCount).indexOfFirst { getMediaItemAt(it).entryId() == entryId }

/** Queue indices in play order: the shuffle order when [shuffled] (by default while shuffle is on). */
internal fun Player.playOrder(shuffled: Boolean = shuffleModeEnabled): List<Int> {
    val timeline = currentTimeline
    return buildList {
        var index = timeline.getFirstWindowIndex(shuffled)
        while (index != C.INDEX_UNSET) {
            add(index)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
        }
    }
}

/**
 * The shuffle order after the [inserted] queue indices were added: right after [current] ("play next"), or at the end
 * when they were [appended] ("add to queue"); the rest keeps its order. The player alone would scatter them randomly.
 */
internal fun shuffleWithInserted(
    order: List<Int>,
    inserted: IntRange,
    current: Int,
    appended: Boolean,
): List<Int> {
    val rest = order.filterNot { it in inserted }
    val at = if (appended) rest.size else rest.indexOf(current) + 1
    return rest.take(at) + inserted + rest.drop(at)
}
