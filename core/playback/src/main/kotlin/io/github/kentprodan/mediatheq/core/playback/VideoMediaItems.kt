package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A movie or an episode in the session's player (ADR 0047): `mediatheq://video/<mediaId>`, marked by an extra, so every
 * music listener (resume, lyrics, prefetch, the saved queue) leaves it alone through [queuedTrackId].
 */
internal fun videoUri(itemId: MediaId): Uri = "$TRACK_SCHEME://video/${Uri.encode(itemId.toString())}".toUri()

/** The video id inside a [videoUri], or `null` for any other URI. */
internal fun Uri.videoId(): MediaId? =
    if (scheme == TRACK_SCHEME && host == "video") {
        lastPathSegment?.let { runCatching { MediaId.parse(it) }.getOrNull() }
    } else {
        null
    }

/**
 * What a controller sends for [this] video: id, display metadata, the video mark and where it starts ([startPosition],
 * also asked of the server so a conversion begins there); the session adds the URI.
 */
internal fun Video.toMediaItem(
    artworkUrl: String?,
    startPosition: Duration = Duration.ZERO,
): MediaItem =
    MediaItem
        .Builder()
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata
                .Builder()
                .setTitle(title)
                // "Series · S2 E4" for an episode, the year for a movie: the notification's second line.
                .setArtist(subtitle())
                .setDurationMs(duration.inWholeMilliseconds.takeIf { it > 0 })
                .setArtworkUri(artworkUrl?.toUri())
                .setMediaType(
                    if (kind == VideoKind.Movie) MediaMetadata.MEDIA_TYPE_MOVIE else MediaMetadata.MEDIA_TYPE_TV_SHOW,
                ).setExtras(
                    Bundle().apply {
                        putBoolean(EXTRA_VIDEO, true)
                        putLong(EXTRA_START_MS, startPosition.inWholeMilliseconds)
                        putArtwork(artwork)
                    },
                ).setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        ).build()

private fun Video.subtitle(): String? =
    when (kind) {
        VideoKind.Movie -> {
            year?.toString()
        }

        VideoKind.Episode -> {
            val number = listOfNotNull(seasonNumber?.let { "S$it" }, episodeNumber?.let { "E$it" }).joinToString(" ")
            listOfNotNull(seriesName, number.ifEmpty { null }).joinToString(" · ").ifEmpty { null }
        }
    }

/** A movie or an episode, not a track (the extra survives the controller-to-session hop; the URI does not). */
internal fun MediaItem.isVideo(): Boolean = mediaMetadata.extras?.getBoolean(EXTRA_VIDEO, false) == true

/** Where the video starts, as its item asked. */
internal fun MediaItem.videoStart(): Duration = (mediaMetadata.extras?.getLong(EXTRA_START_MS, 0L) ?: 0L).milliseconds

private const val EXTRA_VIDEO = "io.github.kentprodan.mediatheq.VIDEO"
private const val EXTRA_START_MS = "io.github.kentprodan.mediatheq.VIDEO_START"
