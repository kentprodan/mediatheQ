package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import io.github.kentprodan.mediatheq.core.data.PlaybackBehavior
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.IdentityHashMap
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * The player the session exposes, so the app, the notification, headsets and Android Auto get the same queue rules:
 * - shuffle: entries added through "play next" play right after the current one (after earlier Play next entries with
 *   [PlaybackBehavior.preservePlayNextOrder]) and "add to queue" entries play last ([shuffleWithInserted]); shuffle
 *   turned on mid-queue goes on with the playing track; new orders are weighted ([weightedShuffle]) or uniform
 *   ([shuffleFrom]) per [PlaybackBehavior.weightedShuffle];
 * - next / previous follow Settings -> Playback -> Playback (#138): rewind first, circular (next and previous stay
 *   available at the ends), keep paused, stop repeat one;
 * - a new queue resets repeat (and shuffle, unless asked for) unless [PlaybackBehavior.retainPlayerState]; never while
 *   the system resumes the saved queue ([resuming]: QueuePersistence puts its state back).
 *
 * Automatic skips (a track that cannot load) use the ExoPlayer itself, not these rules.
 */
@UnstableApi
internal class QueuePlayer(
    private val player: ExoPlayer,
    private val behavior: () -> PlaybackBehavior = { PlaybackBehavior() },
    private val resuming: () -> Boolean = { false },
    private val random: Random = Random,
    private val now: () -> Instant = { Clock.System.now() },
) : ForwardingPlayer(player) {
    // Each listener (the session's) behind one that adds the circular skips to its available commands.
    private val wrapped = IdentityHashMap<Player.Listener, Player.Listener>()

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        // Before the change, so its event already sees the new order. (Album Shuffle sets the mode before its new
        // queue, which then gets an order of its own: reshuffling the old one here costs nothing.)
        if (shuffleModeEnabled && !this.shuffleModeEnabled && mediaItemCount > 0) {
            setOrder(newOrder(first = currentMediaItemIndex))
        }
        super.setShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) = setMediaItems(mediaItems, true)

    /** A shuffled new queue (Shuffle sets the mode first): it starts where its order starts. */
    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        resetPosition: Boolean,
    ) {
        val resumed = resuming()
        if (!resumed && !behavior().retainPlayerState) repeatMode = REPEAT_MODE_OFF
        super.setMediaItems(mediaItems, resetPosition)
        if (resumed || !shuffleModeEnabled || mediaItems.isEmpty() || !behavior().weightedShuffle) return
        setOrder(newOrder(first = null))
        if (resetPosition) seekToDefaultPosition(currentTimeline.getFirstWindowIndex(true))
    }

    /** An unshuffled new queue at a chosen track (Play, a tap); shuffled on only when the state is retained. */
    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        val resumed = resuming()
        if (!resumed && !behavior().retainPlayerState) {
            repeatMode = REPEAT_MODE_OFF
            super.setShuffleModeEnabled(false)
        }
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
        if (!resumed && shuffleModeEnabled && mediaItems.isNotEmpty()) setOrder(newOrder(first = currentMediaItemIndex))
    }

    override fun addMediaItem(mediaItem: MediaItem) = addMediaItems(mediaItemCount, listOf(mediaItem))

    override fun addMediaItem(
        index: Int,
        mediaItem: MediaItem,
    ) = addMediaItems(index, listOf(mediaItem))

    override fun addMediaItems(mediaItems: List<MediaItem>) = addMediaItems(mediaItemCount, mediaItems)

    override fun addMediaItems(
        index: Int,
        mediaItems: List<MediaItem>,
    ) {
        val requested = index.coerceAtMost(mediaItemCount)
        val keepsOrder =
            behavior().preservePlayNextOrder && mediaItems.isNotEmpty() && mediaItems.all { it.isPlayedNext() } &&
                mediaItemCount > 0 && requested == currentMediaItemIndex + 1
        val run =
            if (keepsOrder) {
                playedNextRun(
                    playOrder(),
                    currentMediaItemIndex,
                ) { getMediaItemAt(it).isPlayedNext() }
            } else {
                0
            }
        // Unshuffled, play order is queue order: the new entries go after the run in the queue itself.
        val at = if (shuffleModeEnabled) requested else requested + run
        val appended = at == mediaItemCount
        super.addMediaItems(at, mediaItems)
        if (!shuffleModeEnabled || mediaItems.isEmpty()) return
        setOrder(shuffleWithInserted(playOrder(), at until at + mediaItems.size, currentMediaItemIndex, appended, run))
    }

    override fun seekToNext() = skip { next() }

    override fun seekToNextMediaItem() = skip { next() }

    override fun seekToPrevious() = skip { previous(behavior().rewindFirst) }

    override fun seekToPreviousMediaItem() = skip { previous(rewindFirst = false) }

    override fun getAvailableCommands(): Player.Commands = withSkips(super.getAvailableCommands())

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun addListener(listener: Player.Listener) {
        super.addListener(wrapped.getOrPut(listener) { withSkipsFor(listener) })
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(wrapped.remove(listener) ?: listener)
    }

    /** The behavior settings changed: listeners (the session) get the available commands again (circular). */
    fun onBehaviorChanged() {
        val commands = availableCommands
        wrapped.keys.forEach { it.onAvailableCommandsChanged(commands) }
    }

    /** A manual skip: stop repeat one first; afterwards, unless paused skips stay paused, play. */
    private inline fun skip(move: () -> Boolean) {
        val rules = behavior()
        val paused = !playWhenReady
        if (rules.stopRepeatOneOnSkip && repeatMode == REPEAT_MODE_ONE) repeatMode = REPEAT_MODE_ALL
        if (!move() || !paused || rules.keepPausedOnSkip) return
        if (playbackState == STATE_IDLE || playbackState == STATE_ENDED) prepare()
        play()
    }

    private fun wraps() = behavior().circular || repeatMode == REPEAT_MODE_ALL

    private fun next(): Boolean {
        val order = playOrder()
        val place = nextPlace(order.indexOf(currentMediaItemIndex), order.size, wraps()) ?: return false
        seekToDefaultPosition(order[place])
        return true
    }

    private fun previous(rewindFirst: Boolean): Boolean {
        val order = playOrder()
        val place = order.indexOf(currentMediaItemIndex)
        // The media item variant never rewinds: at the first entry without wrapping it does nothing (as Media3).
        val position = if (rewindFirst) currentPosition.milliseconds else 0.milliseconds
        when (val action = previousAction(position, place, order.size, rewindFirst, wraps())) {
            PreviousAction.Rewind -> if (rewindFirst || place > 0) seekTo(0) else return false
            is PreviousAction.To -> seekToDefaultPosition(order[action.place])
        }
        return true
    }

    private fun newOrder(first: Int?): List<Int> {
        val count = mediaItemCount
        return if (behavior().weightedShuffle) {
            val weights = (0 until count).map { getMediaItemAt(it).shuffleWeight().weight(now()) }
            weightedShuffle(weights, first, random)
        } else {
            first?.let { shuffleFrom(it, count, random) } ?: (0 until count).shuffled(random)
        }
    }

    private fun setOrder(order: List<Int>) =
        player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), random.nextLong()))

    /** With circular navigation next and previous stay available at the ends (Media3 drops them there). */
    private fun withSkips(commands: Player.Commands): Player.Commands =
        if (behavior().circular && mediaItemCount > 1) commands.buildUpon().addAll(*SKIPS).build() else commands

    /**
     * [listener] with [withSkips] applied to `onAvailableCommandsChanged` (the session sends that value to controllers,
     * it does not ask again). A proxy, so every other callback reaches the listener as it is: Kotlin's `by` delegation
     * does not forward the Java default methods `Player.Listener` is made of, and a hand-written copy of its ~40
     * callbacks would silently miss new ones.
     */
    private fun withSkipsFor(listener: Player.Listener): Player.Listener =
        Proxy.newProxyInstance(Player.Listener::class.java.classLoader, arrayOf(Player.Listener::class.java)) {
            proxy,
            method,
            args,
            ->
            when (method.name) {
                // Identity, so the player can find it again to remove it.
                "equals" -> {
                    proxy === args?.get(0)
                }

                "hashCode" -> {
                    System.identityHashCode(proxy)
                }

                else -> {
                    val forwarded =
                        if (method.name == "onAvailableCommandsChanged") {
                            arrayOf<Any?>(withSkips(args!![0] as Player.Commands))
                        } else {
                            args ?: emptyArray()
                        }
                    try {
                        method.invoke(listener, *forwarded)
                    } catch (e: InvocationTargetException) {
                        throw e.targetException
                    }
                }
            }
        } as Player.Listener

    private companion object {
        val SKIPS =
            intArrayOf(
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
    }
}
