package io.github.kentprodan.mediatheq.feature.downloads

import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.Track

sealed interface DownloadsUiState {
    data object Loading : DownloadsUiState

    data object Empty : DownloadsUiState

    /** Downloads grouped by album, in album order; [storageUsed] in bytes. */
    data class Content(
        val albums: List<DownloadedAlbum>,
        val storageUsed: Long,
    ) : DownloadsUiState
}

/** One album's downloads; [title] is `null` for tracks without an album. */
data class DownloadedAlbum(
    val title: String?,
    val downloads: List<TrackDownload>,
)

sealed interface DownloadsAction {
    /** Plays the finished downloads from [track] on (or [track] alone when it is not finished). */
    data class Play(
        val track: Track,
    ) : DownloadsAction

    data class PlayNext(
        val track: Track,
    ) : DownloadsAction

    data class AddToQueue(
        val track: Track,
    ) : DownloadsAction

    /** Downloads a failed track again. */
    data class Retry(
        val track: Track,
    ) : DownloadsAction

    data class Remove(
        val track: Track,
    ) : DownloadsAction
}
