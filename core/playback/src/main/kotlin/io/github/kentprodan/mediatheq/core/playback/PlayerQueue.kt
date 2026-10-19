package io.github.kentprodan.mediatheq.core.playback

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
