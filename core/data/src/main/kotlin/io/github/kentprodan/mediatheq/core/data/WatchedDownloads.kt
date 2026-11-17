package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Video
import kotlin.time.Instant

/**
 * The video downloads made by hand to remove now (#413), with the origins each loses (Movie, Season, Single; a
 * followed series' own origin is left to smart downloads, music is never judged). Kept: [playing], favorites, what is
 * not watched on the server, a play older than the download (watched before it, Moonfin's rule) or than [since] (the
 * setting turned on later), and a play more recent than the [removal] delay.
 */
fun watchedRemovals(
    downloads: List<VideoDownload>,
    videos: Map<MediaId, Video>,
    removal: WatchedRemoval,
    since: Instant?,
    now: Instant,
    playing: MediaId?,
): Map<MediaId, Set<DownloadOrigin>> {
    val delay = removal.delay ?: return emptyMap()
    if (since == null) return emptyMap()
    return downloads
        .mapNotNull { download ->
            val manual = download.origins.filter(::isManual).toSet()
            val video = videos[download.id]
            val played = video?.userData?.lastPlayedAt
            val completed = download.completedAt
            val removable =
                manual.isNotEmpty() &&
                    download.state == DownloadState.Completed &&
                    download.id != playing &&
                    video != null &&
                    video.userData.played &&
                    !video.userData.isFavorite &&
                    played != null &&
                    completed != null &&
                    played > completed &&
                    played > since &&
                    now - played >= delay
            if (removable) download.id to manual else null
        }.toMap()
}

private fun isManual(origin: DownloadOrigin) =
    origin is DownloadOrigin.Movie || origin is DownloadOrigin.Season || origin == DownloadOrigin.Single
