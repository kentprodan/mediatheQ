package io.github.kentprodan.mediatheq.core.playback

import android.os.SystemClock
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
 * - a controller's stop (car, watch, headset key) pauses instead while [stopPauses] (#141);
 * - pause and stop wait for the fade out, and play cancels it (#142);
 * - Bluetooth lyrics: the metadata's title is the lyrics line set with [setLyricsTitle], the real title its
 *   display title (#143, ADR 0034). The queue's items keep their own metadata.
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
    private val uptime: () -> Long = SystemClock::uptimeMillis,
    private val stopPauses: () -> Boolean = { false },
    /** Runs the pause or stop it is given after the fade out (#142, [VolumeFader.fadeOutThen]). */
    private val fadeOut: (() -> Unit) -> Unit = { it() },
    /** Play was asked for: a running fade out is cancelled. */
    private val onPlay: () -> Unit = {},
    /** Ends a running fade out now ([VolumeFader.finish]): a skip must see the player paused. */
    private val finishFade: () -> Unit = {},
) : ForwardingPlayer(player) {
    // When shuffle was last turned on: Shuffle sends it right before its new queue.
    private var shuffleAskedAt: Long? = null

    // Each listener (the session's) behind one that adds the circular skips to its available commands.
    private val wrapped = IdentityHashMap<Player.Listener, Player.Listener>()

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        // Before the change, so its event already sees the new order. (Album Shuffle sets the mode before its new
        // queue, which then gets an order of its own: reshuffling the old one here costs nothing.)
        if (shuffleModeEnabled) shuffleAskedAt = uptime()
        if (shuffleModeEnabled && !this.shuffleModeEnabled && mediaItemCount > 0) {
            setOrder(newOrder(first = currentMediaItemIndex))
        }
        super.setShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) = setMediaItems(mediaItems, true)

    /**
     * A new queue from its start: Shuffle's (it turns the mode on right before, and the queue starts where its order
     * starts), or a controller's without a start index (Android Auto playing a playlist), which follows the
     * new-queue rule like Play.
     */
    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        resetPosition: Boolean,
    ) {
        val resumed = resuming()
        val askedShuffle = shuffleAskedAt?.let { uptime() - it < SHUFFLE_ASKED_WINDOW_MS } == true
        shuffleAskedAt = null
        if (!resumed && !behavior().retainPlayerState) {
            repeatMode = REPEAT_MODE_OFF
            if (!askedShuffle) super.setShuffleModeEnabled(false)
        }
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
        shuffleAskedAt = null
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

    // The lyrics line shown as the title (#143), and the queue entry it belongs to; null = the real title.
    private var lyricsTitle: String? = null
    private var lyricsEntry: String? = null

    /**
     * The lyrics line Bluetooth displays show as the title of the entry playing now, or `null` for the real one;
     * listeners are told. The line ends with its entry: the next track never carries it, not even for the moment
     * before it is cleared.
     */
    fun setLyricsTitle(line: String?) {
        val entry = currentMediaItem?.entryId()
        if (line == lyricsTitle && (line == null || entry == lyricsEntry)) return
        lyricsTitle = line
        lyricsEntry = entry
        val metadata = mediaMetadata
        wrapped.keys.forEach { it.onMediaMetadataChanged(metadata) }
    }

    override fun getMediaMetadata(): MediaMetadata = withLyricsTitle(super.getMediaMetadata())

    private fun withLyricsTitle(metadata: MediaMetadata): MediaMetadata {
        val line = lyricsTitle ?: return metadata
        if (currentMediaItem?.entryId() != lyricsEntry) return metadata
        return metadata
            .buildUpon()
            .setTitle(line)
            .setDisplayTitle(metadata.displayTitle ?: metadata.title)
            .build()
    }

    override fun stop() = if (stopPauses()) pause() else fadeOut { super.stop() }

    override fun pause() = fadeOut { super.pause() }

    override fun play() {
        onPlay()
        super.play()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) play() else pause()

    override fun seekToNext() = skip { next() }

    override fun seekToNextMediaItem() = skip { next() }

    override fun seekToPrevious() = skip { previous(behavior().rewindFirst, mediaItemOnly = false) }

    override fun seekToPreviousMediaItem() = skip { previous(rewindFirst = false, mediaItemOnly = true) }

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
        finishFade()
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

    private fun previous(
        rewindFirst: Boolean,
        mediaItemOnly: Boolean,
    ): Boolean {
        val order = playOrder()
        val place = order.indexOf(currentMediaItemIndex)
        val position = if (rewindFirst) currentPosition.milliseconds else 0.milliseconds
        when (val action = previousAction(position, place, order.size, rewindFirst, wraps())) {
            // Previous restarts the first track (as Media3); the media item variant does nothing there.
            PreviousAction.Rewind -> if (mediaItemOnly) return false else seekTo(0)

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
     * it does not ask again) and the lyrics title to `onMediaMetadataChanged`. A proxy, so every other callback reaches the listener as it is: Kotlin's `by` delegation
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
                        when (method.name) {
                            "onAvailableCommandsChanged" -> arrayOf<Any?>(withSkips(args!![0] as Player.Commands))

                            // The session keeps the value it is given: it must be the one the getter returns.
                            "onMediaMetadataChanged" -> arrayOf<Any?>(withLyricsTitle(args!![0] as MediaMetadata))

                            else -> args ?: emptyArray()
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
        /** Shuffle and its queue arrive as two commands, one right after the other. */
        const val SHUFFLE_ASKED_WINDOW_MS = 1_000L

        val SKIPS =
            intArrayOf(
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
    }
}
