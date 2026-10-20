package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A snapshot of this player for screens (call on the player's thread). */
@UnstableApi
internal fun Player.toPlayerUiModel(): PlayerUiModel {
    val current = currentMediaItem
    val item = current?.toNowPlayingItem()
    return PlayerUiModel(
        item = item,
        isPlaying = !Util.shouldShowPlayButton(this),
        duration =
            duration.takeIf { it != C.TIME_UNSET }?.milliseconds
                ?: current?.mediaMetadata?.durationMs?.milliseconds
                ?: Duration.ZERO,
        isSeekable = item != null && isCurrentMediaItemSeekable,
        hasNext = hasNextMediaItem(),
        // Previous also restarts the current track, so it is there whenever something is queued.
        hasPrevious = item != null,
        currentEntryId = current?.entryId(),
        shuffle = shuffleModeEnabled,
    )
}

/** The queue in play order (the shuffled order while shuffle is on), for the queue screen. */
internal fun Player.toQueueItems(): List<QueueItem> =
    playOrder().mapNotNull { index ->
        val item = getMediaItemAt(index)
        val id = runCatching { MediaId.parse(item.mediaId) }.getOrNull() ?: return@mapNotNull null
        QueueItem(
            entryId = item.entryId() ?: index.toString(),
            id = id,
            title =
                item.mediaMetadata.title
                    ?.toString()
                    .orEmpty(),
            artist =
                item.mediaMetadata.artist
                    ?.toString()
                    .orEmpty(),
            artwork = item.mediaMetadata.extras?.toArtwork(),
            duration = item.mediaMetadata.durationMs?.milliseconds ?: Duration.ZERO,
        )
    }

private fun MediaItem.toNowPlayingItem(): NowPlayingItem? {
    val id = runCatching { MediaId.parse(mediaId) }.getOrNull() ?: return null
    return NowPlayingItem(
        id = id,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        artwork = mediaMetadata.extras?.toArtwork(),
    )
}
