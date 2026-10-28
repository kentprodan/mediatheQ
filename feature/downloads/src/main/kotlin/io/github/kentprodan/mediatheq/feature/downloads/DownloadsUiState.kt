package io.github.kentprodan.mediatheq.feature.downloads

import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.time.Duration

sealed interface DownloadsUiState {
    data object Loading : DownloadsUiState

    data object Empty : DownloadsUiState

    /** The Downloads tab (#168): totals, what is still downloading, then the finished downloads by collection. */
    data class Content(
        val totals: DownloadTotals,
        /** Queued, downloading, waiting for Wi-Fi or failed; the section shows only while there are some. */
        val downloading: List<TrackDownload> = emptyList(),
        val albums: List<DownloadedAlbum> = emptyList(),
        val playlists: List<DownloadedPlaylist> = emptyList(),
        /** Tracks downloaded on their own. */
        val songs: List<TrackDownload> = emptyList(),
    ) : DownloadsUiState
}

/** What the finished downloads take: [bytes] and [duration], and the [freeBytes] left on the device. */
data class DownloadTotals(
    val bytes: Long,
    val duration: Duration,
    val freeBytes: Long,
)

/** An album downloaded as a whole, with what of it is on the device. */
data class DownloadedAlbum(
    val album: Album,
    val tracks: List<Track>,
    val bytes: Long,
    val duration: Duration,
)

/** A playlist downloaded as a whole, with what of it is on the device. */
data class DownloadedPlaylist(
    val playlist: Playlist,
    val tracks: List<Track>,
    val bytes: Long,
    val duration: Duration,
)

sealed interface DownloadsAction {
    /** Plays the downloaded songs from [track] on (or [track] alone when it is not among them). */
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

    /** Cancels or deletes a download, whatever it was downloaded through. */
    data class Remove(
        val track: Track,
    ) : DownloadsAction

    /** Removes a song downloaded on its own; an album or playlist that keeps it keeps it. */
    data class RemoveSong(
        val track: Track,
    ) : DownloadsAction

    /** Plays a collection's downloaded tracks, shuffled or in order. */
    data class PlayCollection(
        val tracks: List<Track>,
        val shuffle: Boolean,
    ) : DownloadsAction

    /** Removes a collection's downloads, keeping tracks another collection keeps. */
    data class RemoveCollection(
        val origin: DownloadOrigin,
    ) : DownloadsAction
}
