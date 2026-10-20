package io.github.kentprodan.mediatheq.core.playback

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import io.github.kentprodan.mediatheq.core.data.QueueEntryId
import io.github.kentprodan.mediatheq.core.data.QueuePlace
import io.github.kentprodan.mediatheq.core.data.QueueRepository
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.CancellationException
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
 * Keeps the queue saved (media3 skill, section 11) and brings it back paused when the service starts again. The entries
 * are rewritten only when the queue changes; the place (entry, position) is saved after pauses, seeks and track
 * changes, and every [tick] while playing, so a killed process loses at most that much position. [scope] must run on
 * the player's thread.
 */
@UnstableApi
internal class QueuePersistence(
    private val player: ExoPlayer,
    private val repository: QueueRepository,
    private val scope: CoroutineScope,
    private val artworkUrl: suspend (Artwork) -> String?,
    private val debounce: Duration = 1.seconds,
    private val tick: Duration = 10.seconds,
) {
    // Bumped on every queue change; the entries are rewritten until a write has caught up with it.
    private var queueVersion = 0
    private var savedVersion = 0
    private var pending: Job? = null
    private val writes = Mutex()

    private val listener =
        object : Player.Listener {
            // Counted in the direct callbacks: onEvents comes later, and saveNow must already see the change.
            override fun onTimelineChanged(
                timeline: Timeline,
                reason: Int,
            ) {
                // Not source updates: those come each time a track prepares, and the entries stay the same.
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) queueVersion++
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                queueVersion++
            }

            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (events.containsAny(*SAVED_ON)) saveSoon()
            }
        }

    /** Restores the saved queue unless something was queued meanwhile, then starts saving. */
    fun start(): Job =
        scope.launch {
            // Listening first: what is queued while the restore runs is saved too, and the restored queue itself is
            // rewritten once, so the saved positions match the entries left after a sync dropped some.
            player.addListener(listener)
            val saved =
                try {
                    repository.restore()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Nothing to bring back; saving goes on.
                    Log.w(TAG, "Could not restore the saved queue", e)
                    null
                }
            if (saved != null && player.mediaItemCount == 0) {
                val items =
                    saved.entries.map { entry ->
                        entry.track
                            .toMediaItem(entry.track.artwork?.let { artworkUrl(it) }, entry.entryId)
                            .withTrackUri()!!
                    }
                // Not prepared: that would open streams (and server sessions) before anyone presses play.
                player.playWhenReady = false
                player.setMediaItems(items, saved.place.index, saved.place.position.inWholeMilliseconds)
                player.setShuffleOrder(DefaultShuffleOrder(saved.shuffleOrder.toIntArray(), Random.nextLong()))
                player.shuffleModeEnabled = saved.place.shuffle
            }
            while (isActive) {
                delay(tick)
                if (player.isPlaying) save()
            }
        }

    /** Saves now, e.g. when the service is destroyed (read here, written on [scope] after the player is gone). */
    fun saveNow() {
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

    private suspend fun save() = snapshot().write()

    private fun snapshot(): Snapshot {
        val version = queueVersion
        val entries =
            if (version != savedVersion) {
                (0 until player.mediaItemCount).mapNotNull { index ->
                    val item = player.getMediaItemAt(index)
                    val trackId = runCatching { MediaId.parse(item.mediaId) }.getOrNull()
                    trackId?.let { QueueEntryId(item.entryId() ?: newEntryId(), it) }
                }
            } else {
                null
            }
        val place =
            QueuePlace(
                index = player.currentMediaItemIndex,
                position = player.currentPosition.milliseconds,
                shuffle = player.shuffleModeEnabled,
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
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_IS_PLAYING_CHANGED,
            )
    }
}
