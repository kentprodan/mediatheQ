package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Tracks kept on the device for offline play (media3 skill, section 10). Removing a download only deletes the local
 * copy, never anything on the server (jellyfin R5).
 */
interface DownloadRepository {
    /** Every download of the active account's server (#147) with its track, in album order (album, disc, track number). */
    val downloads: Flow<List<TrackDownload>>

    /**
     * The tracks fully on the device, once the stored downloads are read (never a too-early empty set); changes only
     * when a download completes or goes away, not with progress.
     */
    val completedIds: Flow<Set<MediaId>>

    /** Bytes still free where the downloads are kept. */
    val freeBytes: Flow<Long>

    /**
     * Queues [tracks] for download through [origin] (#168); tracks already downloaded or queued stay as they are but
     * gain the origin. `null` retries without touching the origins.
     */
    fun download(
        tracks: List<Track>,
        origin: DownloadOrigin? = DownloadOrigin.Single,
    )

    /** Deletes the local copies of [ids], whatever they were downloaded through. */
    fun remove(ids: List<MediaId>)

    /** Deletes every download of [serverId] (an account removed, #147); returns once they are gone or after a while. */
    suspend fun removeServer(serverId: ServerId)

    /** Forgets an album or playlist [origin] and deletes the tracks no other origin keeps; ignores [DownloadOrigin.Single]. */
    fun removeCollection(origin: DownloadOrigin)

    /** Removes [id] from the songs downloaded on their own; deletes it only when no album or playlist keeps it. */
    fun removeSong(id: MediaId) = removeOrigin(listOf(id), DownloadOrigin.Single)

    /** Forgets [origin] for [ids]; deletes the ones no other origin keeps (a favorite track no longer one, #145). */
    fun removeOrigin(
        ids: List<MediaId>,
        origin: DownloadOrigin,
    )

    /** The user paused every download (#149); downloads resume at the next app start. */
    val paused: Flow<Boolean>

    /** Pauses every download (#149). Main thread. */
    fun pauseAll()

    /** Resumes the downloads the user paused (#149). Main thread. */
    fun resumeAll()

    /**
     * What Cleanup would delete now (#149): failed downloads of the active server and data in the download cache that
     * belongs to no download of any server. Never a completed or waiting download.
     */
    suspend fun cleanupPreview(): CleanupPreview

    /** Deletes what [cleanupPreview] describes, chosen again now; a download that started meanwhile is kept. */
    suspend fun cleanup()

    /**
     * True when [id] is fully on the device. Safe from any thread; right after app start it may read the disk, so call
     * it off the main thread (the player asks on its loader thread).
     */
    fun isDownloaded(id: MediaId): Boolean
}

/** What Cleanup would delete: [failed] downloads, and [bytes] with their partial copies and the leftover data. */
data class CleanupPreview(
    val failed: Int,
    val bytes: Long,
)

/** One download: its track and where it is. */
data class TrackDownload(
    val track: Track,
    val state: DownloadState,
    /** The server's conversion, as the device cannot play the original (e.g. Dolby Atmos on a Pixel, #68). */
    val converted: Boolean = false,
    /** Bytes on the device so far. */
    val bytes: Long = 0,
    /** What it was downloaded through (#168). */
    val origins: Set<DownloadOrigin> = emptySet(),
)
