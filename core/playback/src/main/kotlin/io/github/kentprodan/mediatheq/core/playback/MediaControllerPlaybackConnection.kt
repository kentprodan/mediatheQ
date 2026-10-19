package io.github.kentprodan.mediatheq.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [PlaybackConnection] over a [MediaController] to [MediatheqPlaybackService] (architecture skill, section 7). Commands
 * run on the application scope, so they finish even if the screen that sent them is gone; a newer command replaces an
 * unfinished older one, so two quick taps never play the first tap's queue.
 */
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

        override fun play(
            queue: List<Track>,
            startIndex: Int,
            shuffle: Boolean,
        ) {
            if (queue.isEmpty()) return
            playJob?.cancel()
            playJob =
                scope.launch(Dispatchers.Main.immediate) {
                    val items =
                        queue.map { track ->
                            track.toMediaItem(track.artwork?.let { artworkUrls.url(it, NOTIFICATION_ART_WIDTH) })
                        }
                    val connected =
                        try {
                            connect().await()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Rejected or the service died while connecting: try again on the next command.
                            controller = null
                            return@launch
                        }
                    connected.playQueue(items, startIndex, shuffle)
                }
        }

        private fun connect(): ListenableFuture<MediaController> =
            controller ?: MediaController
                .Builder(context, SessionToken(context, ComponentName(context, MediatheqPlaybackService::class.java)))
                .setListener(
                    object : MediaController.Listener {
                        // The service went away (e.g. stopped after the task was removed): connect again next time.
                        override fun onDisconnected(controller: MediaController) {
                            this@MediaControllerPlaybackConnection.controller = null
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
            const val NOTIFICATION_ART_WIDTH = 512
        }
    }
