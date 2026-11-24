package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * The one player everything in the service holds (#219, ADR 0047): an [ExoPlayer] that forwards every call to the
 * [active] ExoPlayer, so a crossfade can hand playback to a second ExoPlayer without anyone noticing. It keeps the
 * listeners itself and moves them in [swapTo], so they only ever hear the active player.
 *
 * It owns the volume as well: [ExoPlayer.setVolume] (normalization, fades, ducking) sets a target, and the active
 * player plays at target x [gain], the crossfade's share. Main thread only, like the player.
 */
@UnstableApi
internal class SwapPlayer(
    initial: ExoPlayer,
) : InvocationHandler {
    var active: ExoPlayer = initial
        private set

    /** The player to hand out instead of [active]. */
    val player: ExoPlayer =
        Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java), this) as ExoPlayer

    private val listeners = mutableListOf<Player.Listener>()
    private val analyticsListeners = mutableListOf<AnalyticsListener>()
    private val offloadListeners = mutableListOf<ExoPlayer.AudioOffloadListener>()

    /** What [ExoPlayer.setVolume] asked for. */
    var volume = active.volume
        private set

    /** The crossfade's share of [volume] for the active player, 0 to 1. */
    var gain = 1f
        set(value) {
            field = value
            active.volume = volume * value
        }

    /**
     * Makes [next] the active player: every listener moves to it and it takes the volume. The caller has given it the
     * queue and state it should have, and tells the listeners what changed.
     */
    fun swapTo(next: ExoPlayer) {
        if (next === active) return
        listeners.forEach {
            active.removeListener(it)
            next.addListener(it)
        }
        analyticsListeners.forEach {
            active.removeAnalyticsListener(it)
            next.addAnalyticsListener(it)
        }
        offloadListeners.forEach {
            active.removeAudioOffloadListener(it)
            next.addAudioOffloadListener(it)
        }
        active = next
        next.volume = volume * gain
    }

    /** Every [Player.Listener] registered through [player], for events the caller sends at a swap. */
    fun listeners(): List<Player.Listener> = listeners.toList()

    override fun invoke(
        proxy: Any,
        method: Method,
        args: Array<out Any?>?,
    ): Any? {
        val arg = args?.firstOrNull()
        when (method.name) {
            "equals" -> {
                return proxy === arg
            }

            "hashCode" -> {
                return System.identityHashCode(proxy)
            }

            "toString" -> {
                return "SwapPlayer($active)"
            }

            "setVolume" -> {
                volume = arg as Float
                active.volume = volume * gain
                return null
            }

            "getVolume" -> {
                return volume
            }

            "addListener" -> {
                listeners.addOnce(arg as Player.Listener)
            }

            "removeListener" -> {
                listeners.removeIdentity(arg)
            }

            "addAnalyticsListener" -> {
                analyticsListeners.addOnce(arg as AnalyticsListener)
            }

            "removeAnalyticsListener" -> {
                analyticsListeners.removeIdentity(arg)
            }

            "addAudioOffloadListener" -> {
                offloadListeners.addOnce(arg as ExoPlayer.AudioOffloadListener)
            }

            "removeAudioOffloadListener" -> {
                offloadListeners.removeIdentity(arg)
            }
        }
        return try {
            method.invoke(active, *(args ?: emptyArray()))
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private fun <T : Any> MutableList<T>.addOnce(item: T) {
        if (none { it === item }) add(item)
    }

    private fun <T : Any> MutableList<T>.removeIdentity(item: Any?) {
        removeAll { it === item }
    }
}
