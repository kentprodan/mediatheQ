package io.github.kentprodan.mediatheq.core.playback

import android.os.Handler
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import io.github.kentprodan.mediatheq.core.data.FadeCurve
import kotlin.random.Random

/**
 * Crossfades between tracks with two ExoPlayers under one [SwapPlayer] (#219, ADR 0047):
 * - [PREPARE_LEAD_MS] before the crossfade, a second player gets the same queue, shuffle order and repeat mode, starts
 *   prepared at the next item (after its silent intro with smart fades), silent, and shares the audio session, so the
 *   equalizer and loudness effects never change;
 * - at the crossfade's start it plays, the swap player moves to it, and everyone hears one automatic transition (the
 *   outgoing track counted as played to its end); the outgoing player plays its tail unobserved while the volumes move
 *   along the curves, then stops and waits as the next spare.
 *
 * A pause during the crossfade silences the tail at once and pauses the new track as usual; a seek, a skip or a queue
 * change ends the tail too. Anything going wrong before the start (no plan, the second player not ready, an error)
 * leaves the plain transition. Main thread only.
 */
@UnstableApi
internal class Crossfader(
    private val swap: SwapPlayer,
    /** A new ExoPlayer built like the first. */
    private val newPlayer: () -> ExoPlayer,
    /** Whether a crossfade length is set: no checks at all otherwise (offload needs the CPU asleep). */
    private val enabled: () -> Boolean,
    /** The crossfade from [current] (of [durationMs]) into [next], or `null` for a plain transition. */
    private val plan: (current: MediaItem, next: MediaItem, durationMs: Long) -> Plan?,
    private val handler: Handler,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    /** A crossfade began (true) or its tail ended (false). */
    private val onRunning: (Boolean) -> Unit = {},
    private val log: (String) -> Unit = {},
) : Player.Listener {
    data class Plan(
        val lengthMs: Int,
        /** Where the crossfade starts in the outgoing track. */
        val startMs: Long,
        /** Where the incoming track starts. */
        val introSkipMs: Long,
        val outCurve: FadeCurve,
        val inCurve: FadeCurve,
    )

    private var spare: ExoPlayer? = null

    /** The second player while it prepares the next item, and the plan it was prepared for. */
    private var incoming: ExoPlayer? = null
    private var incomingPlan: Plan? = null

    /** The transition given up on (not ready in time, an error): not tried again. */
    private var givenUp: Pair<MediaItem, Int>? = null

    /** The outgoing player during the crossfade. */
    private var tail: ExoPlayer? = null
    private var tailVolume = 1f
    private var fadeStart = 0L
    private var fadePlan: Plan? = null

    /** Our own synthetic events are not reactions to anything. */
    private var announcing = false

    val running: Boolean get() = tail != null

    private val tick =
        Runnable {
            step()
            schedule()
        }

    private val incomingListener =
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                log("crossfade: the next track failed to prepare (${error.errorCodeName}), plain transition")
                givenUp = swap.active.currentMediaItem?.let { it to swap.active.currentMediaItemIndex }
                cancelPrepare()
            }
        }

    fun start() {
        swap.player.addListener(this)
        schedule()
    }

    /** The crossfade setting changed. */
    fun onSettingsChanged() {
        if (!enabled()) cancelPrepare()
        schedule()
    }

    fun release() {
        handler.removeCallbacks(tick)
        swap.player.removeListener(this)
        cancelPrepare()
        finishTail()
        spare?.release()
        spare = null
    }

    private fun schedule() {
        handler.removeCallbacks(tick)
        when {
            tail != null -> handler.postDelayed(tick, FADE_STEP_MS)
            enabled() && swap.player.isPlaying -> handler.postDelayed(tick, CHECK_MS)
        }
    }

    /** One check or fade step; [schedule] runs it, tests call it. */
    @VisibleForTesting
    internal fun step() {
        tail?.let {
            fade(it)
            return
        }
        if (!enabled()) return
        val player = swap.active
        val current = player.currentMediaItem ?: return
        val nextIndex = player.nextMediaItemIndex
        val duration = player.duration
        if (nextIndex == C.INDEX_UNSET || duration == C.TIME_UNSET ||
            givenUp == current to player.currentMediaItemIndex
        ) {
            cancelPrepare()
            return
        }
        val position = player.currentPosition
        val plan = incomingPlan ?: plan(current, player.getMediaItemAt(nextIndex), duration) ?: return cancelPrepare()
        if (incoming == null && position >= plan.startMs - PREPARE_LEAD_MS) prepare(player, nextIndex, plan)
        val next = incoming ?: return
        if (position < plan.startMs) return
        if (next.playbackState == Player.STATE_READY) {
            begin(player, next, plan)
        } else if (duration - position < GIVE_UP_MS) {
            log("crossfade: the next track was not ready in time, plain transition")
            givenUp = current to player.currentMediaItemIndex
            cancelPrepare()
        }
    }

    private fun prepare(
        player: ExoPlayer,
        nextIndex: Int,
        plan: Plan,
    ) {
        val next = spare ?: newPlayer()
        spare = null
        incoming = next
        incomingPlan = plan
        // One audio session: the equalizer and the loudness effect are the session's, so they never reset.
        next.setAudioSessionId(player.audioSessionId)
        next.trackSelectionParameters = player.trackSelectionParameters
        next.repeatMode = player.repeatMode
        next.setMediaItems(List(player.mediaItemCount) { player.getMediaItemAt(it) }, nextIndex, plan.introSkipMs)
        next.shuffleModeEnabled = player.shuffleModeEnabled
        next.setShuffleOrder(DefaultShuffleOrder(shuffleOrder(player.currentTimeline), Random.nextLong()))
        next.pauseAtEndOfMediaItems = false
        next.volume = 0f
        next.playWhenReady = false
        next.addListener(incomingListener)
        next.prepare()
        log("crossfade: preparing the next track, ${plan.lengthMs} ms from ${plan.startMs} ms")
    }

    private fun begin(
        outgoing: ExoPlayer,
        next: ExoPlayer,
        plan: Plan,
    ) {
        next.removeListener(incomingListener)
        incoming = null
        incomingPlan = null
        // The tail must not go on into the next track by itself.
        outgoing.pauseAtEndOfMediaItems = true
        tailVolume = outgoing.volume
        val left = positionOf(outgoing, outgoing.duration)
        swap.gain = 0f
        // Playing before the swap, so the listeners never see the music stop.
        next.play()
        swap.swapTo(next)
        tail = outgoing
        fadePlan = plan
        fadeStart = now()
        onRunning(true)
        announce(left, next)
        log("crossfade: started")
    }

    /** What the listeners would have heard on an automatic transition from [left] to [next]'s current item. */
    private fun announce(
        left: Player.PositionInfo,
        next: ExoPlayer,
    ) {
        val arrived = positionOf(next, next.currentPosition)
        val item = next.currentMediaItem
        val events =
            Player.Events(
                FlagSet
                    .Builder()
                    .addAll(
                        Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TRACKS_CHANGED,
                        Player.EVENT_MEDIA_METADATA_CHANGED,
                    ).build(),
            )
        announcing = true
        try {
            val listeners = swap.listeners()
            listeners.forEach {
                it.onPositionDiscontinuity(left, arrived, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
                it.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
                it.onTracksChanged(next.currentTracks)
                it.onMediaMetadataChanged(next.mediaMetadata)
            }
            listeners.forEach { it.onEvents(swap.player, events) }
        } finally {
            announcing = false
        }
    }

    private fun fade(outgoing: ExoPlayer) {
        val plan = fadePlan ?: return finishTail()
        val progress = (now() - fadeStart).toFloat() / plan.lengthMs
        swap.gain = CrossfadePlan.fadeIn(progress, plan.inCurve)
        outgoing.volume = tailVolume * CrossfadePlan.fadeOut(progress, plan.outCurve)
        if (progress >= 1f || outgoing.playbackState == Player.STATE_ENDED || !outgoing.playWhenReady) finishTail()
    }

    private fun finishTail() {
        val outgoing = tail ?: return
        tail = null
        fadePlan = null
        outgoing.stop()
        outgoing.clearMediaItems()
        outgoing.pauseAtEndOfMediaItems = false
        outgoing.volume = 1f
        swap.gain = 1f
        spare = outgoing
        onRunning(false)
        log("crossfade: ended")
    }

    private fun cancelPrepare() {
        val next = incoming ?: return
        incoming = null
        incomingPlan = null
        next.removeListener(incomingListener)
        next.stop()
        next.clearMediaItems()
        spare = next
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) = schedule()

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        if (announcing || playWhenReady) return
        // A pause during the crossfade: the tail goes silent, the new track pauses as usual.
        finishTail()
        cancelPrepare()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (announcing) return
        // A seek, a skip or a removal ends the tail; the plain transition makes the prepared player useless.
        if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) finishTail()
        cancelPrepare()
        givenUp = null
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) cancelPrepare()
    }

    override fun onRepeatModeChanged(repeatMode: Int) = cancelPrepare()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = cancelPrepare()

    private companion object {
        /** The second player gets this long to load the next track before the crossfade. */
        const val PREPARE_LEAD_MS = 5_000L

        /** Not ready this close to the end: the plain transition. */
        const val GIVE_UP_MS = 500L

        const val CHECK_MS = 250L

        /** About two display frames: a smooth enough volume ramp. */
        const val FADE_STEP_MS = 30L

        fun positionOf(
            player: ExoPlayer,
            positionMs: Long,
        ) = Player.PositionInfo(
            null,
            player.currentMediaItemIndex,
            player.currentMediaItem,
            null,
            player.currentPeriodIndex,
            positionMs,
            positionMs,
            C.INDEX_UNSET,
            C.INDEX_UNSET,
        )

        /** The play order of [timeline]'s items with shuffle on. */
        fun shuffleOrder(timeline: Timeline): IntArray {
            val order = ArrayList<Int>(timeline.windowCount)
            var index = timeline.getFirstWindowIndex(true)
            while (index != C.INDEX_UNSET) {
                order += index
                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
            }
            return order.toIntArray()
        }
    }
}
