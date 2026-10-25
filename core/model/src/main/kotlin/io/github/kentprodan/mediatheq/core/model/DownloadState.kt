package io.github.kentprodan.mediatheq.core.model

/** Where a track's download is on this device. */
sealed interface DownloadState {
    /** Waiting for its turn. */
    data object Queued : DownloadState

    /** Waiting for an unmetered network (downloads are Wi-Fi only by default). */
    data object WaitingForWifi : DownloadState

    /** In progress; [progress] is 0..1, or `null` while the size is not known yet. */
    data class Downloading(
        val progress: Float?,
    ) : DownloadState

    /** On the device: plays without network. */
    data object Completed : DownloadState

    /** Stopped with an error; downloading again retries it. */
    data object Failed : DownloadState
}
