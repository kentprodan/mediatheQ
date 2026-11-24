package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.data.FadeCurve
import kotlin.math.PI
import kotlin.math.sin

/** The crossfade's pure parts (#219): curves, when it may run, and where smart fades put it. */
internal object CrossfadePlan {
    /** The incoming track's volume share at [progress] (0 to 1) through the crossfade. */
    fun fadeIn(
        progress: Float,
        curve: FadeCurve,
    ): Float {
        val p = progress.coerceIn(0f, 1f)
        return when (curve) {
            FadeCurve.Linear -> p
            FadeCurve.EqualPower -> sin(p * PI / 2).toFloat()
            FadeCurve.Exponential -> p * p
        }
    }

    /** The outgoing track's share: the fade in run backwards, so an equal power pair keeps the loudness. */
    fun fadeOut(
        progress: Float,
        curve: FadeCurve,
    ): Float = fadeIn(1f - progress.coerceIn(0f, 1f), curve)

    /** Where a track sits on its album, for [followsOnAlbum]. */
    data class AlbumPlace(
        val albumId: String,
        val disc: Int,
        val track: Int,
    )

    /** [next] is a later track of [current]'s album: an album playing in order, which stays gapless. */
    fun followsOnAlbum(
        current: AlbumPlace?,
        next: AlbumPlace?,
    ): Boolean =
        current != null &&
            next != null &&
            current.albumId == next.albumId &&
            (next.disc to next.track).let { (disc, track) ->
                disc > current.disc || (disc == current.disc && track > current.track)
            }

    /**
     * Whether the transition to the next track crossfades: a length is set, the next track is music that does not
     * follow on the same album, nothing needs the output untouched or a single AudioTrack (bit-perfect, passthrough),
     * repeat one is off, the sleep timer does not end with this track, and the outgoing file is whole on the device
     * (downloaded or fully cached), since the transition ends its server session.
     */
    fun allowed(
        crossfadeMs: Int,
        hasNext: Boolean,
        followsOnAlbum: Boolean,
        video: Boolean,
        bitPerfectWanted: Boolean,
        passthrough: Boolean,
        repeatOne: Boolean,
        sleepTimerAtTrackEnd: Boolean,
        outgoingOnDevice: Boolean,
    ): Boolean =
        crossfadeMs > 0 &&
            hasNext &&
            !followsOnAlbum &&
            !video &&
            !bitPerfectWanted &&
            !passthrough &&
            !repeatOne &&
            !sleepTimerAtTrackEnd &&
            outgoingOnDevice

    /**
     * When the crossfade starts in the outgoing track, in ms: [crossfadeMs] before its end, or with smart fades
     * ([waveform], the track's loudness parts normalized to its loudest, #124) before its quiet tail, so the fade
     * is over when the music is. Never before the track's start.
     */
    fun startMs(
        durationMs: Long,
        crossfadeMs: Int,
        waveform: List<Float>?,
    ): Long {
        val end = waveform?.let { durationMs - quietMs(it.asReversed(), durationMs) } ?: durationMs
        return (end - crossfadeMs).coerceAtLeast(0)
    }

    /** Where the incoming track starts with smart fades, in ms: after its silent intro, [MAX_INTRO_SKIP_MS] at most. */
    fun introSkipMs(
        durationMs: Long,
        waveform: List<Float>?,
    ): Long = waveform?.let { quietMs(it, durationMs).coerceAtMost(MAX_INTRO_SKIP_MS) } ?: 0

    /** The length of the run of quiet parts at the start of [parts], in ms of a track of [durationMs]. */
    private fun quietMs(
        parts: List<Float>,
        durationMs: Long,
    ): Long {
        if (parts.isEmpty()) return 0
        val quiet = parts.takeWhile { it < QUIET }.size
        // All quiet (a silent track or a broken analysis): no smart placement.
        if (quiet == parts.size) return 0
        return durationMs * quiet / parts.size
    }

    /** Below a tenth of the loudest part counts as quiet: fades, room tone, a hidden track's silence. */
    private const val QUIET = 0.1f

    /** A long silent intro is part of the music more often than not. */
    const val MAX_INTRO_SKIP_MS = 3_000L
}
