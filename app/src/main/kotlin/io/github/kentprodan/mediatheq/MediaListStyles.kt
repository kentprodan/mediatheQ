package io.github.kentprodan.mediatheq

import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.TrackInfo
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.MediaListStyle
import io.github.kentprodan.mediatheq.core.ui.TrackDetail

/** The stored Media lists settings (#123) as the lists' style; [downloaded] = tracks, albums and playlists on the phone. */
fun LibraryPreferences.toMediaListStyle(downloaded: Set<MediaId>): MediaListStyle =
    MediaListStyle(
        trackDetails = listOfNotNull(mediaLists.trackInfo.toDetail(), mediaLists.secondTrackInfo.toDetail()),
        halfStars =
            when (ratingMode) {
                RatingMode.WholeStars -> false
                RatingMode.HalfStars -> true
                RatingMode.Off -> null
            },
        centerGridText = mediaLists.centerGridText,
        favoriteOverlay = mediaLists.favoriteOverlay,
        offlineOverlay = mediaLists.offlineOverlay,
        downloaded = downloaded,
    )

private fun TrackInfo.toDetail(): TrackDetail? =
    when (this) {
        TrackInfo.None -> null
        TrackInfo.Duration -> TrackDetail.Duration
        TrackInfo.Favorite -> TrackDetail.Favorite
        TrackInfo.Rating -> TrackDetail.Rating
        TrackInfo.PlayCount -> TrackDetail.PlayCount
        TrackInfo.Offline -> TrackDetail.Offline
        TrackInfo.Codec -> TrackDetail.Codec
        TrackInfo.Bitrate -> TrackDetail.Bitrate
        TrackInfo.CodecAndBitrate -> TrackDetail.CodecAndBitrate
    }
