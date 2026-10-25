package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import java.util.UUID

/**
 * Queue items carry no real URL: `mediatheq://track/<mediaId>` is resolved into a stream only when the player loads it
 * (so a long queue costs nothing up front, and the network at that moment decides the quality).
 */
internal const val TRACK_SCHEME = "mediatheq"

/** Cover width for the notification and system controls. */
internal const val NOTIFICATION_ART_WIDTH = 512

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

/**
 * What a controller sends: id and display metadata only (the session adds the URI). [entryId] tells queue entries
 * apart, since the same track can be queued twice.
 */
internal fun Track.toMediaItem(
    artworkUrl: String?,
    entryId: String = newEntryId(),
): MediaItem =
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
                .setExtras(
                    Bundle().apply {
                        putQueueExtras(entryId, artwork)
                        putGains(this@toMediaItem)
                        putOutputFormat(this@toMediaItem)
                    },
                ).setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        ).build()

/**
 * The session side: gives a controller's item its resolvable URI (items from controllers carry none we trust), and an
 * entry id when it has none; `null` for an item that is not one of our tracks.
 */
internal fun MediaItem.withTrackUri(): MediaItem? {
    val itemId = runCatching { MediaId.parse(mediaId) }.getOrNull() ?: return null
    val builder = buildUpon().setUri(trackUri(itemId))
    if (entryId() == null) {
        val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply { putString(EXTRA_ENTRY_ID, newEntryId()) }
        builder.setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
    }
    return builder.build()
}

/** The queue entry this item is (see [toMediaItem]). */
internal fun MediaItem.entryId(): String? = mediaMetadata.extras?.getString(EXTRA_ENTRY_ID)

internal fun newEntryId(): String = UUID.randomUUID().toString()

// The entry id and the cover travel with the item as plain strings (other processes, e.g. System UI, unbundle the
// extras), so screens can show the cover without a lookup.
private const val EXTRA_ENTRY_ID = "io.github.kentprodan.mediatheq.ENTRY_ID"
private const val EXTRA_ARTWORK_ID = "io.github.kentprodan.mediatheq.ARTWORK_ID"
private const val EXTRA_ARTWORK_TAG = "io.github.kentprodan.mediatheq.ARTWORK_TAG"
private const val EXTRA_ARTWORK_BLUR_HASH = "io.github.kentprodan.mediatheq.ARTWORK_BLUR_HASH"
private const val EXTRA_ALBUM_GAIN = "io.github.kentprodan.mediatheq.ALBUM_GAIN"
private const val EXTRA_TRACK_GAIN = "io.github.kentprodan.mediatheq.TRACK_GAIN"
private const val EXTRA_SAMPLE_RATE = "io.github.kentprodan.mediatheq.SAMPLE_RATE"
private const val EXTRA_CHANNELS = "io.github.kentprodan.mediatheq.CHANNELS"

/** The normalization gains (dB) a queue item carries, so the service sets its volume without a lookup. */
internal data class ItemGains(
    val albumDb: Double?,
    val trackDb: Double?,
)

private fun Bundle.putGains(track: Track) {
    track.albumNormalizationGainDb?.let { putDouble(EXTRA_ALBUM_GAIN, it) }
    track.normalizationGainDb?.let { putDouble(EXTRA_TRACK_GAIN, it) }
}

internal fun MediaItem.gains(): ItemGains {
    val extras = mediaMetadata.extras ?: return ItemGains(null, null)

    fun gain(key: String) = if (extras.containsKey(key)) extras.getDouble(key) else null
    return ItemGains(gain(EXTRA_ALBUM_GAIN), gain(EXTRA_TRACK_GAIN))
}

private fun Bundle.putOutputFormat(track: Track) {
    track.audioFormat?.sampleRateHz?.let { putInt(EXTRA_SAMPLE_RATE, it) }
    track.audioFormat?.channels?.let { putInt(EXTRA_CHANNELS, it) }
}

/**
 * The PCM format this item reaches the sink in when direct played, from the file's metadata (for bit-perfect USB);
 * `null` when unknown. Callers skip transcodes, whose format differs.
 */
internal fun MediaItem.outputFormat(): PcmFormat? {
    val extras = mediaMetadata.extras ?: return null

    fun int(key: String) = if (extras.containsKey(key)) extras.getInt(key) else null
    return MixerAttributeSelector.outputFormat(int(EXTRA_SAMPLE_RATE), int(EXTRA_CHANNELS))
}

private fun Bundle.putQueueExtras(
    entryId: String,
    artwork: Artwork?,
) {
    putString(EXTRA_ENTRY_ID, entryId)
    if (artwork != null) {
        putString(EXTRA_ARTWORK_ID, artwork.itemId)
        putString(EXTRA_ARTWORK_TAG, artwork.tag)
        putString(EXTRA_ARTWORK_BLUR_HASH, artwork.blurHash)
    }
}

internal fun Bundle.toArtwork(): Artwork? {
    val itemId = getString(EXTRA_ARTWORK_ID) ?: return null
    val tag = getString(EXTRA_ARTWORK_TAG) ?: return null
    return Artwork(itemId, tag, getString(EXTRA_ARTWORK_BLUR_HASH))
}
