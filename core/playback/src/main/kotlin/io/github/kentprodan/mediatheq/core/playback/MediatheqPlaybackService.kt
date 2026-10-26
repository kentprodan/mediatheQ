package io.github.kentprodan.mediatheq.core.playback

import android.app.PendingIntent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.cache.Cache
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
import io.github.kentprodan.mediatheq.core.common.DownloadCache
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import io.github.kentprodan.mediatheq.core.data.ArtworkUrlResolver
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LiveSync
import io.github.kentprodan.mediatheq.core.data.PlaybackRepository
import io.github.kentprodan.mediatheq.core.data.QueueRepository
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
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

    @Inject lateinit var queueRepository: QueueRepository

    @Inject lateinit var artworkUrls: ArtworkUrlResolver

    @Inject lateinit var liveSync: LiveSync

    @Inject lateinit var downloads: DownloadRepository

    @Inject
    @DownloadCache
    lateinit var downloadCache: Cache

    @Inject lateinit var networkMonitor: NetworkMonitor

    @Inject lateinit var audioPreferences: AudioPreferencesRepository

    @Inject
    @StreamingCache
    lateinit var streamingCache: Cache

    @Inject internal lateinit var streamingEvictor: ResizableLruCacheEvictor

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private var session: MediaLibrarySession? = null
    private lateinit var reporter: PlaybackReporter
    private var signOutWatch: Job? = null
    private lateinit var persistence: QueuePersistence
    private var persistJob: Job? = null
    private lateinit var prefetcher: Prefetcher
    private var prefetchJob: Job? = null
    private lateinit var usbAudio: UsbAudioPort
    private lateinit var audioOutput: AudioOutputController
    private var audioSettingsJob: Job? = null
    private val equalizer = EqualizerEffect(AudioPreferences.EQ_BANDS_HZ)

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

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
                    StreamMediaSourceFactory(
                        DefaultMediaSourceFactory(
                            streamDataSourceFactory(
                                okHttpClient,
                                streamResolver,
                                downloads,
                                downloadCache,
                                streamingCache,
                                networkMonitor::isOnline,
                                onOpen = { prefetcher.cancel(it) },
                            ),
                        ).setLoadErrorHandlingPolicy(StreamLoadErrorPolicy()),
                        okHttpClient,
                        choose = {
                            chooseStreamSource(it, streamResolver, downloads, streamingCache, networkMonitor::isOnline)
                        },
                        headers = streamResolver::headers,
                        dispatcher = ioDispatcher,
                        onOpen = { prefetcher.cancel(it) },
                    ),
                ).build()
        startAudioOutput(player)
        reporter =
            PlaybackReporter(
                repository = playbackRepository,
                streamOf = streamResolver::current,
                scope = applicationScope,
                playerDispatcher = Dispatchers.Main,
            )
        player.addListener(ReportingListener(player, reporter))
        player.addListener(SkipFailedTrackListener(player) { session?.sendPlaybackError(packageName, it) })
        // Live updates keep going in the background while music plays (issue #18): while it is meant to play, so a
        // buffering stall does not drop and reopen the connection.
        player.addListener(
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    if (events.containsAny(Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                        liveSync.setPlaying(player.wantsToPlay())
                    }
                }
            },
        )
        persistence =
            QueuePersistence(
                player = player,
                repository = queueRepository,
                scope = applicationScope + Dispatchers.Main,
                artworkUrl = { artworkUrls.url(it, NOTIFICATION_ART_WIDTH) },
                isLoggedIn = { sessionRepository.session.first() is SessionState.LoggedIn },
            )
        persistJob = persistence.start()
        prefetcher =
            Prefetcher(
                player = player,
                scope = applicationScope + Dispatchers.Main,
                ioDispatcher = ioDispatcher,
                resolver = streamResolver,
                downloads = downloads,
                cache = streamingCache,
                upstream = OkHttpDataSource.Factory(okHttpClient),
                network = networkMonitor,
            )
        prefetchJob = prefetcher.start()
        session =
            MediaLibrarySession
                .Builder(this, QueuePlayer(player), LibraryCallback())
                // Notification artwork through the app client too (cleartext rule, ADR 0013).
                .setBitmapLoader(
                    CacheBitmapLoader(
                        DataSourceBitmapLoader
                            .Builder(this)
                            .setDataSourceFactory(OkHttpDataSource.Factory(okHttpClient))
                            .build(),
                    ),
                ).apply { sessionActivity()?.let(::setSessionActivity) }
                .setSessionExtras(audioOutput.state.toInfo().toBundle())
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

    /**
     * Offload, bit-perfect USB and normalization follow the settings, the output device and the playing track (media3
     * skill, sections 5-7).
     */
    private fun startAudioOutput(player: ExoPlayer) {
        var settings = AudioSettings()
        var offload: Boolean? = null
        var eqGains = AudioPreferences().eqGainsDb
        lateinit var normalization: NormalizationListener

        fun applyEqualizer() =
            equalizer.apply(player.audioSessionId, audioOutput.state.mode == AudioMode.Processed, eqGains)

        // How the playing track is delivered; `null` when stopped or not resolved yet (the ready refresh catches it).
        fun currentPlayMethod(): PlayMethod? {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return null
            val id = player.currentMediaItem?.let { runCatching { MediaId.parse(it.mediaId) }.getOrNull() }
            return id?.let { streamResolver.current(it)?.playMethod }
        }
        usbAudio = UsbAudioPort.create(getSystemService(AudioManager::class.java))
        audioOutput =
            AudioOutputController(
                port = usbAudio,
                currentFormat = {
                    // A transcode has another format than the file: only a direct play can be bit-perfect.
                    if (currentPlayMethod() == PlayMethod.DirectPlay) player.currentMediaItem?.outputFormat() else null
                },
                onChange = { state ->
                    if (state.offload != offload) player.applyOffload(state.offload).also { offload = state.offload }
                    normalization.apply()
                    applyEqualizer()
                    session?.sessionExtras = state.toInfo().toBundle()
                },
                atmosConverted = {
                    AudioPolicy.atmosConverted(player.currentMediaItem?.isDolbyAtmos() == true, currentPlayMethod())
                },
            )
        normalization = NormalizationListener(player, { settings }) { audioOutput.state.mode }
        player.addListener(normalization)
        player.applyOffload(audioOutput.state.offload).also { offload = audioOutput.state.offload }
        player.addListener(
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                        audioOutput.refresh()
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) = applyEqualizer()
            },
        )
        usbAudio.watch(audioOutput::refresh)
        audioSettingsJob =
            applicationScope.launch(Dispatchers.Main) {
                audioPreferences.preferences.collect {
                    settings =
                        settings.copy(
                            normalization = it.normalization,
                            equalizerOn = it.equalizerOn,
                            bitPerfectUsb = it.bitPerfectUsb,
                        )
                    eqGains = it.eqGainsDb
                    streamingEvictor.maxBytes = gibibytes(it.streamingCacheGb)
                    audioOutput.setSettings(settings)
                    // The output state may not change with the normalization, so set the volume here too.
                    normalization.apply()
                    applyEqualizer()
                }
            }
    }

    private fun AudioOutputState.toInfo() =
        AudioOutputInfo(
            bitPerfectSampleRateHz = bitPerfectFormat?.sampleRateHz,
            usbDacConnected = route == AudioRoute.UsbDac,
            bitPerfectNoMatch = bitPerfectNoMatch,
            bitPerfectSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            atmosConverted = atmosConverted,
        )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Stops with the task only when nothing is playing; otherwise music keeps going (with its notification). */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            // The process may die soon after; end the server session (and any transcode) now.
            if (player != null) reporter.ended(player.currentPosition.milliseconds)
            stopSelf()
        }
    }

    override fun onDestroy() {
        signOutWatch?.cancel()
        persistJob?.cancel()
        prefetchJob?.cancel()
        prefetcher.stop()
        audioSettingsJob?.cancel()
        audioOutput.release()
        usbAudio.release()
        equalizer.release()
        session?.run {
            persistence.saveNow()
            reporter.ended(player.currentPosition.milliseconds)
            player.release()
            release()
        }
        session = null
        liveSync.setPlaying(false)
        super.onDestroy()
    }

    private fun sessionActivity(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    /** The cover's bytes from the server (app client, ADR 0013), or `null` when it does not answer in time. */
    private suspend fun fetchCover(uri: Uri): ByteArray? =
        withContext(ioDispatcher) {
            try {
                okHttpClient
                    .newBuilder()
                    .callTimeout(COVER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()
                    .newCall(Request.Builder().url(uri.toString()).build())
                    .execute()
                    .use { if (it.isSuccessful) it.body.bytes() else null }
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null // not an http(s) URL
            }
        }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        /** Our app, the media notification and trusted system controllers only. */
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> =
            Futures.immediateFuture(
                if (controller.packageName == packageName || controller.isTrusted ||
                    session.isMediaNotificationController(controller)
                ) {
                    acceptWithoutPlayerVolume(session, controller)
                } else {
                    MediaSession.ConnectionResult.reject()
                },
            )

        /** The system asks to play with nothing queued (media button, media card): the saved queue (#43). */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            persistence.resumptionFuture(isForPlayback, ::fetchCover)

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            // Only our tracks can play; anything else (e.g. a voice search without an id) is refused, not crashed on.
            val resolved =
                mediaItems.map {
                    it.withTrackUri()
                        ?: return Futures.immediateFailedFuture(UnsupportedOperationException())
                }
            return Futures.immediateFuture(resolved.toMutableList())
        }
    }
}

/** Music is meant to play: play was asked for and the player has something to play (buffering counts). */
internal fun Player.wantsToPlay(): Boolean =
    playWhenReady && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED

// The boot card waits at most this long for its cover; without it the card shows title and artist only.
private const val COVER_TIMEOUT_SECONDS = 2L
