package io.github.kentprodan.mediatheq

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.NewEpisodeWatcher
import io.github.kentprodan.mediatheq.core.data.VideoPreferencesRepository
import io.github.kentprodan.mediatheq.core.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts "<Series>: S3 · E2 is available" for each new episode of a followed series (#446), on its own channel, when
 * the setting is on and notifications are allowed; a tap opens the series.
 */
@Singleton
class EpisodeNotifications
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val watcher: NewEpisodeWatcher,
        private val preferences: VideoPreferencesRepository,
        private val downloads: DownloadRepository,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        fun start() {
            watcher.start()
            scope.launch {
                watcher.notices.collect { episodes ->
                    if (!preferences.preferences.first().newEpisodeNotifications) return@collect
                    val manager = NotificationManagerCompat.from(context)
                    if (!manager.areNotificationsEnabled()) return@collect
                    createChannel()
                    val downloaded = downloads.completedIds.first()
                    episodes.forEach { post(manager, it, it.id in downloaded) }
                }
            }
        }

        private fun createChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.notification_new_episodes),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        @android.annotation.SuppressLint("MissingPermission")
        private fun post(
            manager: NotificationManagerCompat,
            episode: Video,
            downloaded: Boolean,
        ) {
            val code = listOfNotNull(episode.seasonNumber?.let { "S$it" }, episode.episodeNumber?.let { "E$it" })
            val title = listOfNotNull(episode.seriesName, code.joinToString(" · ").ifEmpty { null }).joinToString(": ")
            val line = if (downloaded) R.string.notification_episode_downloaded else R.string.notification_episode_available
            val text = context.getString(line, episode.title)
            val open =
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_SERIES, episode.seriesId?.toString())
            val tap =
                PendingIntent.getActivity(
                    context,
                    episode.id.hashCode(),
                    open,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(io.github.kentprodan.mediatheq.core.designsystem.R.drawable.mq_ic_tv)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .build()
            runCatching { manager.notify(episode.id.toString(), NOTIFICATION_ID, notification) }
        }

        private companion object {
            const val CHANNEL = "new_episodes"
            const val NOTIFICATION_ID = 446
        }
    }
