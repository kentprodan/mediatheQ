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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PlaybackConnection] over a [MediaController] to [MediatheqPlaybackService] (architecture skill, section 7). Commands
 * run on the application scope, so they finish even if the screen that sent them is gone.
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

        override fun play(
            queue: List<Track>,
            startIndex: Int,
            shuffle: Boolean,
        ) {
            if (queue.isEmpty()) return
            scope.launch {
                val items =
                    queue.map { track ->
                        track.toMediaItem(track.artwork?.let { artworkUrls.url(it, NOTIFICATION_ART_WIDTH) })
                    }
                launch(Dispatchers.Main) {
                    val future = connect()
                    future.addListener(
                        { future.get().playQueue(items, startIndex, shuffle) },
                        ContextCompat.getMainExecutor(context),
                    )
                }
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

        private companion object {
            const val NOTIFICATION_ART_WIDTH = 512
        }
    }
