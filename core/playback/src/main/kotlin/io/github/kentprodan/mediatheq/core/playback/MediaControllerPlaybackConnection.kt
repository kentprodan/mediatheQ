package io.github.kentprodan.mediatheq.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.data.ArtworkUrlResolver
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * [PlaybackConnection] over a [MediaController] to [MediatheqPlaybackService] (architecture skill, section 7). Commands
 * run on the application scope, so they finish even if the screen that sent them is gone; a newer queue replaces an
 * unfinished older one, so two quick taps never play the first tap's queue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
@Singleton
internal class MediaControllerPlaybackConnection
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val artworkUrls: ArtworkUrlResolver,
        @ApplicationScope private val scope: CoroutineScope,
    ) : PlaybackConnection {
        // Touched on the main thread only (Media3 session threading).
        private var controller: ListenableFuture<MediaController>? = null
        private var playJob: Job? = null

        // The connected controller, null while disconnected; the screens' flows follow it across reconnects.
        private val connected = MutableStateFlow<MediaController?>(null)

        override val state: StateFlow<PlayerUiModel> =
            connected
                .flatMapLatest { c -> c?.changes()?.map { c.toPlayerUiModel() } ?: flowOf(PlayerUiModel()) }
                .onStart { command {} }
                .flowOn(Dispatchers.Main.immediate)
                .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlayerUiModel())

        override val position: StateFlow<Duration> =
            connected
                .flatMapLatest { c -> c?.positions(POSITION_TICK) ?: flowOf(Duration.ZERO) }
                .flowOn(Dispatchers.Main.immediate)
                .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Duration.ZERO)

        override fun play(
            queue: List<Track>,
            startIndex: Int,
            shuffle: Boolean,
        ) {
            if (queue.isEmpty()) return
            playJob?.cancel()
            playJob = command { controller -> controller.playQueue(queue.toMediaItems(), startIndex, shuffle) }
        }

        override val queue: StateFlow<List<QueueItem>> =
            connected
                .flatMapLatest { c ->
                    c?.changes(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)?.map {
                        c.toQueueItems()
                    } ?: flowOf(emptyList())
                }.onStart { command {} }
                .flowOn(Dispatchers.Main.immediate)
                .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        override fun playNext(tracks: List<Track>) {
            if (tracks.isEmpty()) return
            command { it.playNext(tracks.toMediaItems()) }
        }

        override fun addToQueue(tracks: List<Track>) {
            if (tracks.isEmpty()) return
            command { it.addToQueue(tracks.toMediaItems()) }
        }

        override fun moveQueueItem(
            entryId: String,
            to: Int,
        ) {
            command { it.moveEntry(entryId, to) }
        }

        override fun removeQueueItem(entryId: String) {
            command { it.removeEntry(entryId) }
        }

        override fun skipToQueueItem(entryId: String) {
            command { it.skipToEntry(entryId) }
        }

        private suspend fun List<Track>.toMediaItems(): List<MediaItem> =
            map { track -> track.toMediaItem(track.artwork?.let { artworkUrls.url(it, NOTIFICATION_ART_WIDTH) }) }

        override fun togglePlayPause() {
            command { Util.handlePlayPauseButtonAction(it) }
        }

        override fun next() {
            command { it.seekToNext() }
        }

        override fun previous() {
            command { it.seekToPrevious() }
        }

        override fun seekTo(position: Duration) {
            command { it.seekTo(position.inWholeMilliseconds) }
        }

        /** Runs [block] with the connected controller on the main thread; a failed connection is retried next time. */
        private fun command(block: suspend (MediaController) -> Unit): Job =
            scope.launch(Dispatchers.Main.immediate) {
                val controller =
                    try {
                        connect().await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Rejected or the service died while connecting: try again on the next command.
                        controller = null
                        return@launch
                    }
                connected.value = controller
                block(controller)
            }

        private fun connect(): ListenableFuture<MediaController> =
            controller ?: MediaController
                .Builder(context, SessionToken(context, ComponentName(context, MediatheqPlaybackService::class.java)))
                .setListener(
                    object : MediaController.Listener {
                        // The service went away (e.g. stopped after the task was removed): connect again next time.
                        override fun onDisconnected(controller: MediaController) {
                            this@MediaControllerPlaybackConnection.controller = null
                            connected.value = null
                        }
                    },
                ).buildAsync()
                .also { controller = it }

        private suspend fun ListenableFuture<MediaController>.await(): MediaController =
            suspendCancellableCoroutine { continuation ->
                addListener(
                    {
                        try {
                            continuation.resume(get())
                        } catch (e: ExecutionException) {
                            continuation.resumeWithException(e.cause ?: e)
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            }

        private companion object {
            val POSITION_TICK = 250.milliseconds
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/**
 * Emits once at once and again after every batch of player events (on the player's thread); with [only], just after
 * batches that hold one of those events.
 */
internal fun Player.changes(vararg only: @Player.Event Int): Flow<Unit> =
    callbackFlow {
        val listener =
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    if (only.isEmpty() || events.containsAny(*only)) trySend(Unit)
                }
            }
        trySend(Unit)
        addListener(listener)
        awaitClose { removeListener(listener) }
    }

/** The position now and after every event (seek, pause, next track); repeated every [tick] only while playing. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Player.positions(tick: Duration): Flow<Duration> =
    changes().flatMapLatest {
        flow {
            do {
                emit(currentPosition.milliseconds)
                delay(tick)
            } while (isPlaying)
        }
    }
