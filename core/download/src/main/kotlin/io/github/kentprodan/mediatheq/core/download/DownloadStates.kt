package io.github.kentprodan.mediatheq.core.download

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import io.github.kentprodan.mediatheq.core.model.DownloadState

/**
 * A Media3 download state as the app shows it, or `null` while it is being removed (gone for the user).
 * [notMetRequirements] are the manager's unmet requirements: a queued download then waits for Wi-Fi.
 */
@UnstableApi
internal fun downloadState(
    @Download.State state: Int,
    percentDownloaded: Float,
    notMetRequirements: Int,
): DownloadState? =
    when (state) {
        Download.STATE_QUEUED, Download.STATE_STOPPED, Download.STATE_RESTARTING -> {
            if (notMetRequirements != 0) DownloadState.WaitingForWifi else DownloadState.Queued
        }

        Download.STATE_DOWNLOADING -> {
            DownloadState.Downloading(
                percentDownloaded.takeUnless { it == C.PERCENTAGE_UNSET.toFloat() }?.let {
                    (it / 100f).coerceIn(
                        0f,
                        1f,
                    )
                },
            )
        }

        Download.STATE_COMPLETED -> {
            DownloadState.Completed
        }

        Download.STATE_FAILED -> {
            DownloadState.Failed
        }

        Download.STATE_REMOVING -> {
            null
        }

        else -> {
            error("Unknown download state $state")
        }
    }
