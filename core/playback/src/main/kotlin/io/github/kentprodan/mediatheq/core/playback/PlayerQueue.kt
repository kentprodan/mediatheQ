package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Replaces the queue and starts playing (PlaybackConnection.play): at [startIndex], or, with [shuffle], at the first
 * track of a fresh shuffle order (so shuffled albums do not always start with track 1; the order is kept for unshuffle).
 */
internal fun Player.playQueue(
    items: List<MediaItem>,
    startIndex: Int,
    shuffle: Boolean,
) {
    shuffleModeEnabled = shuffle
    setMediaItems(items, if (shuffle) 0 else startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0)
    if (shuffle) seekToDefaultPosition(currentTimeline.getFirstWindowIndex(true))
    prepare()
    play()
}
