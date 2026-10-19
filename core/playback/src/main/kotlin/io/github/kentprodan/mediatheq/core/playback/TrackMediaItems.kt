package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track

/**
 * Queue items carry no real URL: `mediatheq://track/<mediaId>` is resolved into a stream only when the player loads it
 * (so a long queue costs nothing up front, and the network at that moment decides the quality).
 */
internal const val TRACK_SCHEME = "mediatheq"

internal fun trackUri(itemId: MediaId): Uri = "$TRACK_SCHEME://track/${Uri.encode(itemId.toString())}".toUri()

/** The track id inside a [trackUri], or `null` for any other URI. */
internal fun Uri.trackId(): MediaId? =
    if (scheme == TRACK_SCHEME &&
        host == "track"
    ) {
        lastPathSegment?.let { runCatching { MediaId.parse(it) }.getOrNull() }
    } else {
        null
    }

/** What a controller sends: id and display metadata only (the session adds the URI). */
internal fun Track.toMediaItem(artworkUrl: String?): MediaItem =
    MediaItem
        .Builder()
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata
                .Builder()
                .setTitle(title)
                .setArtist(artists.joinToString { it.name })
                .setAlbumTitle(albumTitle)
                .setTrackNumber(trackNumber)
                .setDiscNumber(discNumber)
                .setDurationMs(duration.inWholeMilliseconds)
                .setArtworkUri(artworkUrl?.toUri())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        ).build()

/** The session side: gives a controller's item its resolvable URI (items from controllers carry none we trust). */
internal fun MediaItem.withTrackUri(): MediaItem {
    val itemId = runCatching { MediaId.parse(mediaId) }.getOrNull() ?: return this
    return buildUpon().setUri(trackUri(itemId)).build()
}
