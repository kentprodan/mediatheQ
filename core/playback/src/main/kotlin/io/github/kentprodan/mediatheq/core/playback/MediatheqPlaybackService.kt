package io.github.kentprodan.mediatheq.core.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.DownloadCache
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.ArtworkUrlResolver
import io.github.kentprodan.mediatheq.core.data.AudioOutputRepository
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LiveSync
import io.github.kentprodan.mediatheq.core.data.LyricsRepository
import io.github.kentprodan.mediatheq.core.data.OfflineFilter
import io.github.kentprodan.mediatheq.core.data.OutputProfile
import io.github.kentprodan.mediatheq.core.data.PlaybackRepository
import io.github.kentprodan.mediatheq.core.data.QueueRepository
import io.github.kentprodan.mediatheq.core.data.ResumeRepository
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.WaveformRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
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

    @Inject lateinit var appLog: AppLog

    @Inject lateinit var streamResolver: StreamResolver

    @Inject lateinit var playbackRepository: PlaybackRepository

    @Inject lateinit var sessionRepository: SessionRepository

    @Inject lateinit var queueRepository: QueueRepository

    @Inject lateinit var appMode: AppModeRepository

    @Inject lateinit var artworkUrls: ArtworkUrlResolver

    @Inject lateinit var liveSync: LiveSync

    @Inject lateinit var downloads: DownloadRepository

    @Inject lateinit var waveforms: WaveformRepository

    @Inject lateinit var resumes: ResumeRepository

    @Inject lateinit var library: LibraryRepository

    @Inject lateinit var userData: UserDataRepository

    @Inject lateinit var lyricsRepository: LyricsRepository

    @Inject
    @DownloadCache
    lateinit var downloadCache: Cache

    @Inject lateinit var networkMonitor: NetworkMonitor

    @Inject lateinit var audioPreferences: AudioPreferencesRepository

    @Inject lateinit var audioOutputs: AudioOutputRepository

    @Inject lateinit var offlineFilter: OfflineFilter

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
    private lateinit var waveformAnalyzer: WaveformAnalyzer
    private var waveformJob: Job? = null
    private var behaviorJob: Job? = null
    private var buttonsJob: Job? = null
    private var buttonState = ButtonState()
    private lateinit var focusController: AudioFocusController
    private lateinit var autoActions: AutoActionsController
    private lateinit var fader: VolumeFader
    private lateinit var bluetoothLyrics: BluetoothLyrics

    // The audio settings as last read, on the main thread like the player; the defaults until then (milliseconds).
    private var audio = AudioPreferences()
    private lateinit var headsetButtons: HeadsetButtons
    private var transcodeJob: Job? = null

    /** One background thread for waveform analysis (#124): one file at a time, below playback and the UI. */
    private val waveformWorker =
        Executors
            .newSingleThreadExecutor { task ->
                Thread({
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    task.run()
                }, "MqWaveform")
            }.asCoroutineDispatcher()
    private lateinit var usbAudio: AudioOutputPort
    private lateinit var passthroughCapabilities: Media3PassthroughCapabilities
    private lateinit var audioOutput: AudioOutputController
    private lateinit var outputApplier: OutputStateApplier
    private lateinit var reopener: AudioTrackReopener
    private var audioSettingsJob: Job? = null
    private var outputsJob: Job? = null
    private val equalizer by lazy { EqualizerEffect(AudioPreferences.EQ_BANDS_HZ, appLog) }

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onCreate() {
        super.onCreate()
        val playerNetwork =
            // The switch and Automatic offline mode's Not on Wi-Fi (#144); no network is OfflineModeNetwork's own.
            OfflineModeNetwork(networkMonitor, offlineFilter.forced, applicationScope)
        val player =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    // handleAudioFocus = false: AudioFocusController decides (#139, ADR 0031).
                    false,
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
                                playerNetwork::isOnline,
                                onOpen = { prefetcher.cancel(it) },
                            ),
                        ).setLoadErrorHandlingPolicy(StreamLoadErrorPolicy()),
                        okHttpClient,
                        choose = { id, item ->
                            chooseStreamSource(
                                id,
                                streamResolver,
                                downloads,
                                streamingCache,
                                playerNetwork::isOnline,
                                item,
                            )
                        },
                        headers = streamResolver::headers,
                        dispatcher = ioDispatcher,
                        onOpen = { prefetcher.cancel(it) },
                        chooseVideo = { id, item ->
                            chooseVideoSource(id, streamResolver, item.videoStart(), playerNetwork::isOnline)
                        },
                    ),
                ).build()
        startAudioOutput(player)
        autoActions =
            AutoActionsController(this, getSystemService(AudioManager::class.java), player) { audio.autoActions }
        autoActions.start()
        val transcoder =
            InstantTranscoder(
                player,
                enabled = { audioPreferences.preferences.first().forceInstantTranscoding },
                isOnline = playerNetwork::isOnline,
                needsNewStream = { id ->
                    val local = downloads.isDownloaded(id) || streamingCache.isFullyCached(originalCacheKey(id))
                    wantsNewStream(
                        streamResolver.quality(id),
                        // An Atmos track passing through stays the original on a metered network (ADR 0039).
                        streamResolver.requestQuality(id, audioPreferences.preferences.first().meteredQuality),
                        local,
                    )
                },
            )
        transcodeJob =
            applicationScope.launch(Dispatchers.Main) { playerNetwork.meteredChanges.collect(transcoder::onNetwork) }
        reporter =
            PlaybackReporter(
                repository = playbackRepository,
                streamOf = streamResolver::current,
                scope = applicationScope,
                playerDispatcher = Dispatchers.Main,
            )
        player.addListener(ReportingListener(player, reporter))
        resumeListener =
            ResumeListener(
                player,
                record = {
                    track,
                    playlist,
                    position,
                    ->
                    applicationScope.launch { resumes.record(track, position, playlist) }
                },
                scope = applicationScope + Dispatchers.Main,
            ).also(player::addListener)
        player.addListener(
            SkipFailedTrackListener(
                player,
                recover = { error ->
                    // The output refused the AudioTrack: bit-perfect plays on normally, passthrough is tried once more.
                    outputApplier.recover(
                        error,
                    ) { audioOutput.onBitPerfectFailed() || audioOutput.onPassthroughFailed() }
                },
            ) { session?.sendPlaybackError(packageName, it) },
        )
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
                restoresMusic = { appMode.mode.first().music },
                log = appLog,
            )
        persistJob = persistence.start()
        waveformAnalyzer =
            WaveformAnalyzer(
                scope = applicationScope,
                worker = waveformWorker,
                files = LocalFiles(downloads.completedIds, downloadCache, streamingCache)::find,
                decoder = MediaCodecPcmDecoder(appLog),
                waveforms = waveforms,
                completedDownloads = downloads.completedIds,
                log = appLog,
            )
        waveformJob = waveformAnalyzer.start()
        player.addListener(waveformAnalyzer.playerListener)
        prefetcher =
            Prefetcher(
                player = player,
                scope = applicationScope + Dispatchers.Main,
                ioDispatcher = ioDispatcher,
                resolver = streamResolver,
                downloads = downloads,
                cache = streamingCache,
                upstream = OkHttpDataSource.Factory(okHttpClient),
                network = playerNetwork,
                onCached = { waveformAnalyzer.offer(listOf(it)) },
                log = appLog,
            )
        prefetchJob = prefetcher.start()
        // The notification keeps the real title while a lyrics line is the session title (#143, ADR 0034).
        setMediaNotificationProvider(
            object : DefaultMediaNotificationProvider(this) {
                override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? =
                    metadata.displayTitle ?: metadata.title
            },
        )
        session =
            MediaLibrarySession
                .Builder(this, queuePlayer(player), LibraryCallback())
                // Notification artwork through the app client too (cleartext rule, ADR 0013); kept covers of downloads
                // are `file:` URIs (#154), read locally.
                .setBitmapLoader(
                    CacheBitmapLoader(
                        DataSourceBitmapLoader
                            .Builder(this)
                            .setDataSourceFactory(
                                DefaultDataSource.Factory(this, OkHttpDataSource.Factory(okHttpClient)),
                            ).build(),
                    ),
                ).apply { sessionActivity()?.let(::setSessionActivity) }
                .setSessionExtras(audioOutput.state.toInfo().toBundle())
                .build()
        startSessionButtons(player)
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

    /** The session's player: Settings -> Playback -> Playback applies to every controller alike (#138). */
    private fun queuePlayer(player: ExoPlayer): QueuePlayer {
        val queuePlayer =
            QueuePlayer(
                player,
                behavior = { audio.behavior },
                resuming = { persistence.resuming },
                stopPauses = { autoActions.stopPauses() },
                fadeOut = fader::fadeOutThen,
                onPlay = fader::cancelFadeOut,
                finishFade = fader::finish,
            )
        bluetoothLyrics =
            BluetoothLyrics(
                player,
                enabled = { audio.behavior.bluetoothLyrics },
                load = { (lyricsRepository.lyrics(it) as? MqResult.Success)?.value },
                scope = applicationScope + Dispatchers.Main,
                schedule = handlerSchedule(Handler(Looper.getMainLooper())),
                onTitle = queuePlayer::setLyricsTitle,
            )
        player.addListener(bluetoothLyrics)
        behaviorJob =
            applicationScope.launch(Dispatchers.Main) {
                audioPreferences.preferences.collect {
                    val behaviorChanged = it.behavior != audio.behavior
                    val lyricsChanged = it.behavior.bluetoothLyrics != audio.behavior.bluetoothLyrics
                    val buttonsChanged = it.notificationButtons != audio.notificationButtons
                    audio = it
                    if (behaviorChanged) queuePlayer.onBehaviorChanged()
                    // Bluetooth lyrics also hide the queue from the platform session (ADR 0034).
                    if (buttonsChanged || lyricsChanged) refreshButtons()
                    if (lyricsChanged) bluetoothLyrics.onSettingChanged()
                }
            }
        headsetButtons =
            HeadsetButtons(queuePlayer, { audio.headset }, handlerSchedule(Handler(Looper.getMainLooper())))
        return queuePlayer
    }

    /**
     * The notification's action buttons (#139): their icons follow repeat, shuffle and the playing track's favorite
     * (from the library, so a favorite set in the app shows at once).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startSessionButtons(player: Player) {
        val current = MutableStateFlow(player.currentMediaItem?.queuedTrackId())
        // A restored queue may already repeat or shuffle: no change event will say so.
        buttonState = buttonState.copy(repeatMode = player.repeatMode, shuffle = player.shuffleModeEnabled)
        player.addListener(
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        current.value =
                            player.currentMediaItem?.queuedTrackId()
                    }
                    if (events.containsAny(
                            Player.EVENT_REPEAT_MODE_CHANGED,
                            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        )
                    ) {
                        buttonState =
                            buttonState.copy(repeatMode = player.repeatMode, shuffle = player.shuffleModeEnabled)
                        refreshButtons()
                    }
                }
            },
        )
        buttonsJob =
            applicationScope.launch(Dispatchers.Main) {
                current
                    .flatMapLatest { id ->
                        id?.let { library.track(it).map { track -> track?.userData?.isFavorite == true } }
                            ?: flowOf(false)
                    }.distinctUntilChanged()
                    .collect {
                        buttonState = buttonState.copy(isFavorite = it)
                        refreshButtons()
                    }
            }
    }

    private fun buttonNames() =
        ButtonNames(
            favorite = getString(R.string.playback_button_favorite),
            unfavorite = getString(R.string.playback_button_unfavorite),
            repeat = getString(R.string.playback_button_repeat),
            shuffle = getString(R.string.playback_button_shuffle),
        )

    private fun currentButtons() = sessionButtons(audio.notificationButtons, buttonState, buttonNames())

    /** The buttons again, and the notification's previous / next as chosen. */
    private fun refreshButtons() {
        val session = session ?: return
        session.setMediaButtonPreferences(currentButtons())
        session.mediaNotificationControllerInfo?.let { controller ->
            val result = connectionFor(session, controller)
            session.setAvailableCommands(controller, result.availableSessionCommands, result.availablePlayerCommands)
        }
    }

    /**
     * Our app, the notification and trusted controllers: the default commands without the player volume, plus the
     * action buttons' commands; the notification's previous / next as Settings -> Notification buttons says.
     */
    private fun connectionFor(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val accepted = acceptWithoutPlayerVolume(session, controller)
        val players =
            if (session.isMediaNotificationController(controller)) {
                notificationPlayerCommands(
                    accepted.availablePlayerCommands,
                    audio.notificationButtons,
                    hideQueue = audio.behavior.bluetoothLyrics,
                )
            } else {
                accepted.availablePlayerCommands
            }
        return MediaSession.ConnectionResult
            .AcceptedResultBuilder(session)
            .setAvailablePlayerCommands(players)
            .setAvailableSessionCommands(withButtonCommands(accepted.availableSessionCommands))
            .setMediaButtonPreferences(currentButtons())
            .build()
    }

    /**
     * Offload, bit-perfect USB and normalization follow the settings, the output device and the playing track (media3
     * skill, sections 5-7).
     */
    private fun startAudioOutput(player: ExoPlayer) {
        var settings = AudioSettings()
        var eqGains = OutputProfile().eqGainsDb
        lateinit var normalization: NormalizationListener
        lateinit var outputProfiles: OutputProfileFollower
        var ducked = false
        focusController =
            AudioFocusController(
                getSystemService(AudioManager::class.java),
                player,
                settings = { audio.audioFocus },
                bitPerfect = { AudioPolicy.fixedVolume(audioOutput.state.mode) },
            ) {
                ducked = it
                normalization.apply()
            }
        player.addListener(focusController)

        fun applyEqualizer() = outputApplier.applyEqualizer()

        fun currentId(): MediaId? =
            player.currentMediaItem?.let { runCatching { MediaId.parse(it.mediaId) }.getOrNull() }

        // How the playing track is delivered; `null` when stopped or not resolved yet (the ready refresh catches it).
        fun currentPlayMethod(): PlayMethod? {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return null
            return currentId()?.let { streamResolver.current(it)?.playMethod }
        }

        fun convertedDownload(): Boolean = currentId()?.let(streamResolver::isConvertedDownload) == true
        // Into the app's log (#169): tracks, play / pause with how the stream comes, errors.
        player.addListener(
            PlaybackLogListener(player, appLog) {
                listOfNotNull(currentPlayMethod()?.name, currentId()?.let(streamResolver::quality)?.name)
                    .joinToString(" · ")
                    .ifEmpty { null }
            },
        )
        player.addAnalyticsListener(VideoLogListener(appLog))
        usbAudio = AudioOutputPort.create(getSystemService(AudioManager::class.java))
        // What HDMI passes through (ADR 0039); a change (plug, the TV's surround setting) decides again.
        passthroughCapabilities = Media3PassthroughCapabilities(this) { audioOutput.onCapabilitiesChanged() }
        // Bit-perfect turned on mid-track: a new AudioTrack, opened once the old one is gone (#165).
        reopener = AudioTrackReopener(player, handlerSchedule(Handler(Looper.getMainLooper())))
        outputApplier =
            OutputStateApplier(
                player,
                state = { audioOutput.state },
                volume = { normalization.apply() },
                equalizer = { sessionId, on -> equalizer.apply(sessionId, on, eqGains) },
                extras = { session?.sessionExtras = it.toInfo(currentPlayMethod(), convertedDownload()).toBundle() },
                device = { outputProfiles.onDevice(it) },
            )
        audioOutput =
            AudioOutputController(
                playingVideo = { player.currentMediaItem?.isVideo() == true },
                port = usbAudio,
                currentFormat = {
                    val playing =
                        player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
                    val method = currentPlayMethod()
                    // A transcode or a converted download has another format than the file: only the file can be.
                    // Not resolved yet (just after a transition): assume the file, the ready refresh corrects it;
                    // dropping the preference meanwhile put the next AudioTrack on the normal mixer (#65).
                    if (playing && (method == null || AudioPolicy.playsOriginal(method, convertedDownload()))) {
                        player.currentMediaItem?.outputFormat()
                    } else {
                        null
                    }
                },
                onChange = {
                    // Before applying: the next stream request already asks for what this route passes through.
                    streamResolver.passthrough = it.requestPassthrough
                    outputApplier.apply(it)
                },
                atmosConverted = {
                    AudioPolicy.atmosConverted(
                        isAtmos = player.currentMediaItem?.isDolbyAtmos() == true,
                        playMethod = currentPlayMethod(),
                        convertedDownload = convertedDownload(),
                    )
                },
                log = { appLog.info(AUDIO_TAG, it) },
                reopen = { reopener.reopen() },
                tracksReleased = { reopener.onTracksReleased() },
                passthroughCapabilities = passthroughCapabilities,
                // Only the file itself can pass through: a transcode or a converted download is PCM or Opus.
                currentMime = {
                    val method = currentPlayMethod()
                    if (method == null || AudioPolicy.playsOriginal(method, convertedDownload())) {
                        player.currentMediaItem?.passthroughMime()
                    } else {
                        null
                    }
                },
                played = {
                    val method = currentPlayMethod()
                    PlayedStream(
                        itemId = player.currentMediaItem?.mediaId,
                        known = method != null,
                        convertedKbps =
                            method?.let {
                                val cap = currentId()?.let(streamResolver::quality)?.bitrate?.div(BITS_PER_KBIT)
                                AudioPolicy.convertedKbps(
                                    it,
                                    currentId()?.let(streamResolver::convertedDownloadKbps),
                                    cap,
                                )
                            },
                    )
                },
            )
        // The device to remember as current: written one after the other, the last one wins (#140).
        val usedDevice = MutableStateFlow<OutputDevice?>(null)
        outputProfiles =
            OutputProfileFollower(
                remember = { usedDevice.value = it },
                apply = { profile ->
                    settings = profile.toAudioSettings()
                    eqGains = profile.eqGainsDb
                    audioOutput.setSettings(settings)
                    // The output state may not change with the normalization, so set the volume here too.
                    normalization.apply()
                    applyEqualizer()
                },
            )
        normalization =
            NormalizationListener(
                player,
                { settings },
                mode = { audioOutput.state.mode },
                duck = { (if (ducked) DUCK_VOLUME else 1f) * fader.factor },
            )
        player.addListener(normalization)
        // Fades (#142): bit-perfect has no volume to ramp.
        fader =
            VolumeFader(
                player,
                settings = { audio.transitions },
                fixedVolume = { AudioPolicy.fixedVolume(audioOutput.state.mode) },
                schedule = handlerSchedule(Handler(Looper.getMainLooper())),
            ) { normalization.apply() }
        player.addListener(fader)
        outputApplier.applyOffload(audioOutput.state.offload)
        player.addListener(
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        audioOutput.refresh("transition")
                    } else if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                        audioOutput.refresh("state ${player.playbackState}")
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) = applyEqualizer()
            },
        )
        usbAudio.watch(audioOutput::onDeviceChanged, audioOutput::onPreferenceDropped)
        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onAudioTrackInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    audioTrackConfig: AudioSink.AudioTrackConfig,
                ) = audioOutput.onAudioTrackOpened(audioTrackConfig.sampleRate, audioTrackConfig.encoding)

                override fun onAudioTrackReleased(
                    eventTime: AnalyticsListener.EventTime,
                    audioTrackConfig: AudioSink.AudioTrackConfig,
                ) = audioOutput.onAudioTrackReleased()
            },
        )
        audioSettingsJob =
            applicationScope.launch(Dispatchers.Main) {
                audioPreferences.preferences.collect { streamingEvictor.maxBytes = gibibytes(it.streamingCacheGb) }
            }
        // Each output's own settings (#140); the device media plays on comes with each output decision.
        outputsJob =
            applicationScope.launch(Dispatchers.Main) {
                launch { audioOutputs.outputs.collect(outputProfiles::onOutputs) }
                usedDevice.filterNotNull().collect { audioOutputs.use(it.id, it.name) }
            }
        // The real device first: the controller's initial state says phone until its first decision.
        audioOutput.refresh("start")
        outputProfiles.onDevice(audioOutput.state.output)
    }

    /** The output for screens; [playMethod] and [convertedDownload] say how the playing track's stream plays. */
    private fun AudioOutputState.toInfo(
        playMethod: PlayMethod? = null,
        convertedDownload: Boolean = false,
    ): AudioOutputInfo {
        val isAtmos = session?.player?.currentMediaItem?.isDolbyAtmos() == true
        val passthroughConfirmed = atmosPassthrough(passthroughEncoding, isAtmosFile = isAtmos)
        return AudioOutputInfo(
            // Only once an AudioTrack really opened on the bit-perfect output (#65).
            bitPerfectSampleRateHz = bitPerfectFormat?.sampleRateHz?.takeIf { bitPerfectConfirmed },
            usbDacConnected = route == AudioRoute.UsbDac,
            bitPerfectNoMatch = bitPerfectNoMatch,
            bitPerfectSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            atmosConverted = atmosConverted,
            atmosPassthrough = passthroughConfirmed,
            atmosDecoded =
                AudioPolicy.atmosDecoded(
                    isAtmos,
                    playMethod,
                    convertedDownload,
                    passthroughConfirmed,
                    decoderUsed = AudioPolicy.decoderUsed(passthroughEncoding, mode),
                ),
            playedItemId = played.itemId,
            streamKnown = played.known,
            convertedKbps = played.convertedKbps,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /**
     * Stops with the task only when nothing is playing; otherwise music keeps going (with its notification), unless
     * "Pause playback on task removal" pauses it first (#141).
     */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // A fade out in progress pauses now: the check below must see it.
        fader.finish()
        autoActions.on(AutoEvent.TaskRemoved)
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            // The process may die soon after; end the server session (and any transcode) now.
            if (player != null) reporter.ended(player.currentPosition.milliseconds)
            stopSelf()
        }
    }

    private var resumeListener: ResumeListener? = null

    override fun onDestroy() {
        signOutWatch?.cancel()
        persistJob?.cancel()
        prefetchJob?.cancel()
        prefetcher.stop()
        waveformJob?.cancel()
        behaviorJob?.cancel()
        buttonsJob?.cancel()
        transcodeJob?.cancel()
        waveformWorker.close()
        audioSettingsJob?.cancel()
        outputsJob?.cancel()
        autoActions.release()
        fader.release()
        bluetoothLyrics.release()
        // A reopen in progress ends now, not on a timer after the player is gone.
        reopener.release()
        audioOutput.release()
        usbAudio.release()
        passthroughCapabilities.release()
        equalizer.release()
        session?.run {
            persistence.saveNow()
            reporter.ended(player.currentPosition.milliseconds)
            resumeListener?.release()
            focusController.release()
            player.release()
            release()
        }
        session = null
        liveSync.setPlaying(false)
        super.onDestroy()
    }

    /** The app, asked to show the player: the TV opens Now Playing (#240); the phone ignores the extra. */
    private fun sessionActivity(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.putExtra(EXTRA_OPEN_PLAYER, true)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    /**
     * The cover's bytes: a download's kept cover from the device (#154), else from the server (app client, ADR 0013);
     * `null` when it does not answer in time.
     */
    private suspend fun fetchCover(uri: Uri): ByteArray? =
        withContext(ioDispatcher) {
            localCoverBytes(uri)?.let { return@withContext it }
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
                    connectionFor(session, controller)
                } else {
                    MediaSession.ConnectionResult.reject()
                },
            )

        /**
         * Headset and Bluetooth buttons (#139): clicks counted and keys remapped per Settings -> Playback -> Headset
         * buttons. Only key events from the system (a platform controller); the notification's buttons are commands.
         */
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean {
            // Swiping the media card away arrives as a stop key with a mark (#141).
            autoActions.onMediaButton(intent)
            if (controllerInfo.controllerVersion != MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION) return false
            val event =
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
            return headsetButtons.onMediaButton(event)
        }

        /** The notification's action buttons (#139). */
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val player = session.player
            when (customCommand.customAction) {
                FAVORITE_COMMAND.customAction -> {
                    val id =
                        player.currentMediaItem?.queuedTrackId()
                            ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    // This track's own value: buttonState may still hold the previous track's just after a skip.
                    applicationScope.launch {
                        userData.setFavorite(
                            id,
                            library
                                .track(id)
                                .first()
                                ?.userData
                                ?.isFavorite != true,
                        )
                    }
                }

                REPEAT_COMMAND.customAction -> {
                    player.repeatMode = nextRepeatMode(player.repeatMode)
                }

                SHUFFLE_COMMAND.customAction -> {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                }

                else -> {
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

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
            // The music place right before a video replaces it: the hand-back brings the queue back there (ADR 0047).
            // Not an empty player (Video mode, or the start-up restore still running): that would save nothing over it.
            if (mediaItems.any { it.isVideo() } && mediaSession.player.mediaItemCount > 0) persistence.saveNow()
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

/** The bytes of a `file:` cover (a download's kept cover, #154), or `null` for any other URI or a missing file. */
internal fun localCoverBytes(uri: Uri): ByteArray? {
    if (uri.scheme != "file") return null
    return try {
        uri.path?.let { File(it).readBytes() }
    } catch (_: IOException) {
        null
    }
}

private const val AUDIO_TAG = "MqAudioOutput"

/** The audio output refused or lost the AudioTrack (not the stream or the decoder). */
@UnstableApi
internal fun PlaybackException.isAudioOutputError(): Boolean =
    generateSequence<Throwable>(this) { it.cause }
        .any { it is AudioSink.InitializationException || it is AudioSink.WriteException }

private const val BITS_PER_KBIT = 1000
