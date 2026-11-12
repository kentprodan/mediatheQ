package io.github.kentprodan.mediatheq.feature.downloads

import io.github.kentprodan.mediatheq.core.data.AutoDownloads
import io.github.kentprodan.mediatheq.core.data.CleanupPreview
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.time.Duration

/** Manage offline files (#149): three tabs over one state. */
sealed interface DownloadsUiState {
    data object Loading : DownloadsUiState

    /**
     * Offline files: the summary and the finished downloads by collection (#168), as [filter] leaves them; Download
     * queue: [downloading]; Auto offline rules: [rules].
     */
    data class Content(
        val totals: DownloadTotals,
        /** Queued, downloading, waiting for Wi-Fi or failed: the Download queue tab. */
        val downloading: List<TrackDownload> = emptyList(),
        val albums: List<DownloadedAlbum> = emptyList(),
        val playlists: List<DownloadedPlaylist> = emptyList(),
        /** Tracks downloaded on their own. */
        val songs: List<TrackDownload> = emptyList(),
        /** The name of the SD card the downloads are on while it is missing (#146): they are kept, not playable. */
        val unavailableOn: String? = null,
        /** The user paused every download (#149). */
        val paused: Boolean = false,
        val rules: AutoRules = AutoRules(),
        /** The Offline files filter as typed. */
        val filter: String = "",
        /** Some download finished, before the filter: the Offline files tab is not empty. */
        val hasFiles: Boolean = false,
        /** The Cleanup confirmation while it is open. */
        val cleanup: CleanupPreview? = null,
    ) : DownloadsUiState
}

/** The automatic downloads of favorites (#145) as Settings -> Media has them. */
data class AutoRules(
    val option: AutoDownloads = AutoDownloads.Disabled,
    val removeUnfavorited: Boolean = true,
)

enum class DownloadsTab { Files, Queue, Rules }

/** How a download in the queue reads; [Paused] wins over waiting, as nothing runs anyway. */
enum class QueueStatus { Downloading, Queued, WaitingForWifi, Paused, Failed }

/** What the finished downloads take: [bytes] and [duration], and the [freeBytes] left on the device. */
data class DownloadTotals(
    val bytes: Long,
    val duration: Duration,
    val freeBytes: Long,
    /** Downloaded by hand: a track with any manual origin counts here (#149). */
    val manual: OfflineUsage = OfflineUsage(),
    /** Kept only by the automatic downloads of favorites (#145). */
    val auto: OfflineUsage = OfflineUsage(),
)

/** [tracks] finished downloads taking [bytes]. */
data class OfflineUsage(
    val tracks: Int = 0,
    val bytes: Long = 0,
)

/** An album downloaded as a whole, with what of it is on the device. */
data class DownloadedAlbum(
    val album: Album,
    val tracks: List<Track>,
    val bytes: Long,
    val duration: Duration,
    /** Only a favorite's automatic download (#145): no Remove, unfavorite it instead. */
    val auto: Boolean = false,
)

/** A playlist downloaded as a whole, with what of it is on the device. */
data class DownloadedPlaylist(
    val playlist: Playlist,
    val tracks: List<Track>,
    val bytes: Long,
    val duration: Duration,
    /** Only a favorite's automatic download (#145): no Remove, unfavorite it instead. */
    val auto: Boolean = false,
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

    data object PauseAll : DownloadsAction

    data object ResumeAll : DownloadsAction

    data class Filter(
        val text: String,
    ) : DownloadsAction

    /** Opens the Cleanup confirmation with what it would delete. */
    data object AskCleanup : DownloadsAction

    data object ConfirmCleanup : DownloadsAction

    data object DismissCleanup : DownloadsAction
}
