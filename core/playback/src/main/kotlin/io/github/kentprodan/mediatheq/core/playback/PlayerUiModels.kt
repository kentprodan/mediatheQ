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
        queueSize = mediaItemCount,
        isPlaying = !Util.shouldShowPlayButton(this),
        duration =
            duration.takeIf { it != C.TIME_UNSET }?.milliseconds
                ?: current?.mediaMetadata?.durationMs?.milliseconds
                ?: Duration.ZERO,
        isSeekable = item != null && isCurrentMediaItemSeekable,
        hasNext = hasNextMediaItem(),
        // Previous also restarts the current track, so it is there whenever something is queued.
        hasPrevious = item != null,
    )
}

private fun MediaItem.toNowPlayingItem(): NowPlayingItem? {
    val id = runCatching { MediaId.parse(mediaId) }.getOrNull() ?: return null
    return NowPlayingItem(
        id = id,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        albumTitle = mediaMetadata.albumTitle?.toString(),
        artwork = mediaMetadata.extras?.toArtwork(),
    )
}
