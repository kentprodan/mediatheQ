package io.github.kentprodan.mediatheq.core.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Runs downloads in the foreground with a progress notification (media3 skill, section 10). No scheduler: on API 31+
 * Media3 cannot restart the service from the background, so unfinished downloads resume when the app opens
 * ([resumeDownloads]).
 */
@UnstableApi
@AndroidEntryPoint
class MediatheqDownloadService :
    DownloadService(
        NOTIFICATION_ID,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        CHANNEL_ID,
        R.string.download_channel_name,
        R.string.download_channel_description,
    ) {
    @Inject lateinit var manager: DownloadManager

    private val notifications by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = manager

    override fun getScheduler(): Scheduler? = null

    // The helper says "waiting for Wi-Fi" itself when the unmetered requirement is not met.
    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification =
        notifications.buildProgressNotification(
            this,
            R.drawable.ic_download_notification,
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            },
            null,
            downloads,
            notMetRequirements,
        )

    private companion object {
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "downloads"
    }
}

/** Resumes unfinished downloads (after a reboot or a killed process). Call from the foreground, e.g. app start. */
@OptIn(UnstableApi::class)
fun resumeDownloads(context: Context) {
    DownloadService.start(context, MediatheqDownloadService::class.java)
}
