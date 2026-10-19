package io.github.kentprodan.mediatheq.core.playback

import android.app.PendingIntent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.data.PlaybackRepository
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The only owner of the player (media3 skill, section 1): ExoPlayer with audio focus and "becoming noisy" pause, a
 * media session for the notification and system controls, server reporting, and sign-out handling.
 */
@UnstableApi
@AndroidEntryPoint
class MediatheqPlaybackService : MediaLibraryService() {
    @Inject lateinit var okHttpClient: OkHttpClient

    @Inject lateinit var streamResolver: StreamResolver

    @Inject lateinit var playbackRepository: PlaybackRepository

    @Inject lateinit var sessionRepository: SessionRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private var session: MediaLibrarySession? = null
    private lateinit var reporter: PlaybackReporter
    private var signOutWatch: Job? = null

    override fun onCreate() {
        super.onCreate()
        val player =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    // handleAudioFocus =
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(streamDataSourceFactory(okHttpClient, streamResolver))
                        .setLoadErrorHandlingPolicy(StreamLoadErrorPolicy()),
                ).build()
        reporter =
            PlaybackReporter(
                repository = playbackRepository,
                streamOf = streamResolver::current,
                onSessionEnded = streamResolver::finished,
                scope = applicationScope,
                playerDispatcher = Dispatchers.Main,
            )
        player.addListener(ReportingListener(player, reporter))
        session =
            MediaLibrarySession
                .Builder(this, player, LibraryCallback())
                // Notification artwork through the app client too (cleartext rule, ADR 0013).
                .setBitmapLoader(
                    CacheBitmapLoader(
                        DataSourceBitmapLoader
                            .Builder(this)
                            .setDataSourceFactory(OkHttpDataSource.Factory(okHttpClient))
                            .build(),
                    ),
                ).apply { sessionActivity()?.let(::setSessionActivity) }
                .build()
        signOutWatch =
            applicationScope.launch(Dispatchers.Main) {
                sessionRepository.session.collect { state ->
                    if (state is SessionState.LoggedOut) {
                        player.stop()
                        player.clearMediaItems()
                    }
                }
            }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Stops with the task only when nothing is playing; otherwise music keeps going (with its notification). */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        signOutWatch?.cancel()
        session?.run {
            reporter.ended(player.currentPosition.milliseconds)
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun sessionActivity(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        /** Our app, the media notification and trusted system controllers only. */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            if (controller.packageName == packageName || controller.isTrusted ||
                session.isMediaNotificationController(controller)
            ) {
                super.onConnect(session, controller)
            } else {
                MediaSession.ConnectionResult.reject()
            }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems
                    .map {
                        it.withTrackUri()
                    }.toMutableList(),
            )
    }
}

/** Turns player events into server reports. */
@UnstableApi
private class ReportingListener(
    private val player: Player,
    private val reporter: PlaybackReporter,
) : Player.Listener {
    private fun currentId(): MediaId? =
        player.currentMediaItem?.mediaId?.let {
            runCatching { MediaId.parse(it) }.getOrNull()
        }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val itemId = currentId() ?: return
        if (isPlaying) {
            reporter.playing(itemId) { player.currentPosition.milliseconds }
        } else if (player.playbackState == Player.STATE_READY) {
            reporter.paused(player.currentPosition.milliseconds)
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (oldPosition.mediaItem?.mediaId != newPosition.mediaItem?.mediaId) {
            // Next track, skip or a new queue: the old one ended where it was (currentPosition is already the new one).
            reporter.ended(oldPosition.positionMs.milliseconds)
        } else if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            reporter.seeked(newPosition.positionMs.milliseconds, player.isPlaying)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) reporter.ended(player.currentPosition.milliseconds)
    }

    override fun onPlayerError(error: PlaybackException) {
        reporter.ended(player.currentPosition.milliseconds, failed = true)
    }
}
