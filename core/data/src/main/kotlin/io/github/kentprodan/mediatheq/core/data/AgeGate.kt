package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.flow.first

/**
 * Whether opening [id] (a movie, an episode, or a [series]) needs the PIN on this profile (#434, #443): a PIN is set and
 * its rating (an episode's own or its series') is above the profile's limit. Unrated items are never gated.
 */
suspend fun ParentalControls.needsPin(
    videos: VideoRepository,
    id: MediaId,
    series: Boolean,
): Boolean {
    val limit = limit.first() ?: return false
    if (!hasPin.first()) return false
    val ratings =
        if (series) {
            listOf(videos.series(id).first()?.officialRating)
        } else {
            val video = videos.video(id).first()
            listOf(video?.officialRating, video?.seriesId?.let { videos.series(it).first()?.officialRating })
        }
    return ratings.any { aboveLimit(it, limit) }
}
