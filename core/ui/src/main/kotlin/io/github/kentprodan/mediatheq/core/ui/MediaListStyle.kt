package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track

/** A detail on the trailing side of a track row (Settings -> Media lists, #123). */
enum class TrackDetail { Duration, Favorite, Rating, PlayCount, Offline, Codec, Bitrate, CodecAndBitrate }

/**
 * How every media list looks (Settings -> Media lists, #123). `:app` builds it from the stored preferences and
 * provides it as [LocalMediaListStyle], so a change applies at once in every list without each screen passing it.
 */
@Immutable
data class MediaListStyle(
    /** Shown top to bottom on the trailing side of track rows; empty = nothing. */
    val trackDetails: List<TrackDetail> = listOf(TrackDetail.Duration),
    /** Ratings in half stars; `null` when ratings are off (no rating detail then). */
    val halfStars: Boolean? = false,
    val centerGridText: Boolean = true,
    val favoriteOverlay: Boolean = true,
    val offlineOverlay: Boolean = false,
    /**
     * Downloaded tracks and fully downloaded albums and playlists. A deliberate exception to "state comes through the
     * UiState": every list shows it, and threading it through each screen adds nothing.
     */
    val downloaded: Set<MediaId> = emptySet(),
)

/** The [MediaListStyle] of the lists below; the defaults until `:app` provides the stored one. */
val LocalMediaListStyle = compositionLocalOf { MediaListStyle() }

/**
 * The text of a text [detail] of [track] (duration, codec, bitrate), `null` when the server did not report it or the
 * detail is not text.
 */
fun trackDetailText(
    track: Track,
    detail: TrackDetail,
): String? {
    val format = track.audioFormat
    val codec = format?.codec?.takeIf { it.isNotBlank() }?.uppercase()
    val bitrate = format?.bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" }
    return when (detail) {
        TrackDetail.Duration -> formatDuration(track.duration)
        TrackDetail.Codec -> codec
        TrackDetail.Bitrate -> bitrate
        TrackDetail.CodecAndBitrate -> listOfNotNull(codec, bitrate).joinToString(" · ").ifEmpty { null }
        TrackDetail.Favorite, TrackDetail.Rating, TrackDetail.PlayCount, TrackDetail.Offline -> null
    }
}
