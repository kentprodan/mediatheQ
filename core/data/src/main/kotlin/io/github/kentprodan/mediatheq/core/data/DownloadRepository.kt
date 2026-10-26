package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Tracks kept on the device for offline play (media3 skill, section 10). Removing a download only deletes the local
 * copy, never anything on the server (jellyfin R5).
 */
interface DownloadRepository {
    /** Every download with its track, in album order (album, disc, track number). */
    val downloads: Flow<List<TrackDownload>>

    /**
     * The tracks fully on the device, once the stored downloads are read (never a too-early empty set); changes only
     * when a download completes or goes away, not with progress.
     */
    val completedIds: Flow<Set<MediaId>>

    /** Bytes the downloads take on this device. */
    val storageUsed: Flow<Long>

    /** Queues [tracks] for download; tracks already downloaded or queued stay as they are. */
    fun download(tracks: List<Track>)

    /** Deletes the local copies of [ids]. */
    fun remove(ids: List<MediaId>)

    /**
     * True when [id] is fully on the device. Safe from any thread; right after app start it may read the disk, so call
     * it off the main thread (the player asks on its loader thread).
     */
    fun isDownloaded(id: MediaId): Boolean
}

/** One download: its track and where it is. */
data class TrackDownload(
    val track: Track,
    val state: DownloadState,
    /** The server's conversion, as the device cannot play the original (e.g. Dolby Atmos on a Pixel, #68). */
    val converted: Boolean = false,
)
