package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.data.QueueEntryId
import io.github.kentprodan.mediatheq.core.data.QueuePlace
import io.github.kentprodan.mediatheq.core.data.QueueRepository
import io.github.kentprodan.mediatheq.core.data.SavedQueue
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the queue saved (media3 skill, section 11) and brings it back paused when the service starts again, also for
 * the system's playback resumption ([resumption]). The entries are rewritten only when the queue changes; the place
 * (entry, position) is saved after pauses, seeks and track changes, and every [tick] while playing, so a killed process
 * loses at most that much position. [scope] must run on the player's thread.
 */
@UnstableApi
internal class QueuePersistence(
    private val player: ExoPlayer,
    private val repository: QueueRepository,
    private val scope: CoroutineScope,
    private val artworkUrl: suspend (Artwork) -> String?,
    /** Nothing comes back while signed out (also when the token could not be read). */
    private val isLoggedIn: suspend () -> Boolean = { true },
    /** `false` in Video mode (ADR 0051): the saved music queue stays saved, not brought back. */
    private val restoresMusic: suspend () -> Boolean = { true },
    private val debounce: Duration = 1.seconds,
    private val tick: Duration = 10.seconds,
    private val log: AppLog = AppLog.None,
) {
    // Bumped on every queue change; the entries are rewritten until a write has caught up with it.
    private var queueVersion = 0
    private var savedVersion = 0
    private var pending: Job? = null
    private val writes = Mutex()

    // Done once the saved queue is back in the player (or there was none).
    private val restored = CompletableDeferred<Unit>()

    // The shuffle order to put back when the session sets the resumed queue again (see resumption).
    private var resumedShuffleOrder: List<Int>? = null

    /** The session is about to set the resumed queue again: QueuePlayer leaves its state alone (#138). */
    val resuming: Boolean get() = resumedShuffleOrder != null

    private val listener =
        object : Player.Listener {
            // Counted in the direct callbacks: onEvents comes later, and saveNow must already see the change.
            override fun onTimelineChanged(
                timeline: Timeline,
                reason: Int,
            ) {
                // Not source updates: those come each time a track prepares, and the entries stay the same.
                if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
                queueVersion++
                val order = resumedShuffleOrder ?: return
                resumedShuffleOrder = null
                if (order.size == player.mediaItemCount) {
                    player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                queueVersion++
            }

            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (events.containsAny(*SAVED_ON)) saveSoon()
                val videoEnded =
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                        player.playbackState == Player.STATE_ENDED &&
                        player.currentMediaItem?.isVideo() == true
                if (videoEnded) handBack()
            }
        }

    /** Restores the saved queue unless something was queued meanwhile, then starts saving. */
    fun start(): Job =
        scope.launch {
            // Listening first: what is queued while the restore runs is saved too, and the restored queue itself is
            // rewritten once, so the saved positions match the entries left after a sync dropped some.
            player.addListener(listener)
            try {
                val restores = restoresMusic()
                val saved =
                    try {
                        if (restores && isLoggedIn()) repository.restore() else null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Nothing to bring back; saving goes on.
                        log.warn(TAG, "Could not restore the saved queue", e)
                        null
                    }
                // Nothing to bring back (none saved, signed out, every track gone, or unreadable): forget it, so the
                // next media button with the app closed does not start a service with nothing to play (#43).
                if (saved == null && restores) forget()
                if (saved != null && player.mediaItemCount == 0) putBack(saved)
            } finally {
                // Also when the restore failed: a resumption waiting for it then gets nothing.
                restored.complete(Unit)
            }
            while (isActive) {
                delay(tick)
                if (player.isPlaying) save()
            }
        }

    private suspend fun forget() {
        try {
            repository.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(TAG, "Could not forget the saved queue", e)
        }
    }

    /**
     * What the system resumes when it asks to play with nothing queued (a media button or the media card with the app
     * closed): the queue [start] brought back, from its entry and position; nothing when none was saved (logout forgets
     * it). Media3 then sets these items again, which would draw a new shuffle order, so the restored one is put back
     * right after. Without [isForPlayback] (System UI building the card at boot) only the current entry, player
     * untouched.
     */
    suspend fun resumption(isForPlayback: Boolean): MediaItemsWithStartPosition {
        restored.await()
        if (player.mediaItemCount == 0) return MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET)
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        if (!isForPlayback) return MediaItemsWithStartPosition(listOf(player.getMediaItemAt(index)), 0, position)
        resumedShuffleOrder = player.playOrder(shuffled = true)
        return MediaItemsWithStartPosition((0 until player.mediaItemCount).map(player::getMediaItemAt), index, position)
    }

    /**
     * [resumption] for the session callback; the boot card gets its cover's bytes from [fetchCover] (System UI loads no
     * server URL itself).
     */
    fun resumptionFuture(
        isForPlayback: Boolean,
        fetchCover: suspend (Uri) -> ByteArray?,
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val result = SettableFuture.create<MediaItemsWithStartPosition>()
        scope.launch {
            try {
                val resumed = resumption(isForPlayback)
                result.set(
                    if (isForPlayback) {
                        resumed
                    } else {
                        MediaItemsWithStartPosition(
                            resumed.mediaItems.map { it.withArtworkData(fetchCover) },
                            resumed.startIndex,
                            resumed.startPositionMs,
                        )
                    },
                )
            } catch (e: CancellationException) {
                result.setException(e)
                throw e
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result
    }

    /** The saved queue in the player, paused at its entry and position, with its shuffle order and repeat mode. */
    private suspend fun putBack(saved: SavedQueue) {
        val items =
            saved.entries.map { entry ->
                entry.track
                    .toMediaItem(
                        entry.track.artwork?.let { artworkUrl(it) },
                        entry.entryId,
                        entry.playlistId,
                    ).withTrackUri()!!
            }
        // Not prepared: that would open streams (and server sessions) before anyone presses play.
        player.playWhenReady = false
        player.setMediaItems(items, saved.place.index, saved.place.position.inWholeMilliseconds)
        player.setShuffleOrder(DefaultShuffleOrder(saved.shuffleOrder.toIntArray(), Random.nextLong()))
        player.shuffleModeEnabled = saved.place.shuffle
        player.repeatMode = saved.place.repeatMode.toPlayerRepeatMode()
    }

    /**
     * A video ended (ADR 0047): the music queue saved before it comes back paused; not in Video mode, where it stays
     * saved, and not when something else was queued meanwhile.
     */
    private fun handBack() =
        scope.launch {
            if (!restoresMusic() || !isLoggedIn()) return@launch
            val saved =
                try {
                    repository.restore()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn(TAG, "Could not bring the music back after a video", e)
                    null
                } ?: return@launch
            if (player.currentMediaItem?.isVideo() == true) putBack(saved)
        }

    /** A video is queued: nothing is saved, so the music queue saved before it stays as it was (ADR 0047). */
    private fun hasVideo(): Boolean = (0 until player.mediaItemCount).any { player.getMediaItemAt(it).isVideo() }

    /** Saves now, e.g. when the service is destroyed (read here, written on [scope] after the player is gone). */
    fun saveNow() {
        if (hasVideo()) return
        pending?.cancel()
        val snapshot = snapshot()
        scope.launch { snapshot.write() }
    }

    private fun saveSoon() {
        pending?.cancel()
        pending =
            scope.launch {
                delay(debounce)
                save()
            }
    }

    private suspend fun save() {
        if (!hasVideo()) snapshot().write()
    }

    private fun snapshot(): Snapshot {
        val version = queueVersion
        val entries =
            if (version != savedVersion) {
                (0 until player.mediaItemCount).mapNotNull { index ->
                    val item = player.getMediaItemAt(index)
                    val trackId = runCatching { MediaId.parse(item.mediaId) }.getOrNull()
                    trackId?.let { QueueEntryId(item.entryId() ?: newEntryId(), it, item.playlistId()) }
                }
            } else {
                null
            }
        val place =
            QueuePlace(
                index = player.currentMediaItemIndex,
                position = player.currentPosition.milliseconds,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode.toRepeatMode(),
            )
        // The shuffle order is saved also while shuffle is off, so the restored player has one of the right length.
        val shuffleOrder = entries?.let { player.playOrder(shuffled = true) }.orEmpty()
        return Snapshot(entries, shuffleOrder, place, version)
    }

    // One write at a time, in the order the snapshots were taken (FIFO lock), so an older one never lands last.
    private suspend fun Snapshot.write() =
        writes.withLock {
            if (entries != null) {
                repository.saveQueue(entries, shuffleOrder, place)
                savedVersion = maxOf(savedVersion, this.queueVersion)
            } else {
                repository.savePlace(place)
            }
        }

    private class Snapshot(
        /** `null` when only the place changed. */
        val entries: List<QueueEntryId>?,
        val shuffleOrder: List<Int>,
        val place: QueuePlace,
        val queueVersion: Int,
    )

    private companion object {
        const val TAG = "QueuePersistence"
        val SAVED_ON =
            intArrayOf(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_IS_PLAYING_CHANGED,
            )
    }
}
