package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.kentprodan.mediatheq.core.data.QueueRepository

/**
 * Play on headphones or a car kit with the app closed (#43): the service starts in the foreground and must then play,
 * or the system kills the app, so it starts only when a queue is saved (logout forgets it). The queue comes back
 * through [MediatheqPlaybackService]'s playback resumption.
 */
@UnstableApi
class MediatheqMediaButtonReceiver : MediaButtonReceiver() {
    override fun shouldStartForegroundService(
        context: Context,
        intent: Intent,
    ): Boolean =
        // shortcut: the flag can be stale once (e.g. every saved track left the library): that press starts the service
        // with nothing to play, and its restore clears the flag (ADR 0020). Check the entries here if it ever shows up.
        EntryPointAccessors
            .fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
            .queueRepository()
            .hasSavedQueue

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface ReceiverEntryPoint {
        fun queueRepository(): QueueRepository
    }
}
