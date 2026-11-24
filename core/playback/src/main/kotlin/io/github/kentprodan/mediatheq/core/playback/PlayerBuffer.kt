package io.github.kentprodan.mediatheq.core.playback

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * How much media the player keeps ahead (#626, engine research 3.6). The byte budget follows the heap: Media3's stock
 * target (about 138 MB for video) is more than a small box's heap, and a 100 Mb/s remux fills it before the time limit
 * is reached. A third of the heap, never more than [MAX_TARGET_BYTES] nor less than Media3's floor. Pure.
 */
internal fun targetBufferBytes(maxHeapBytes: Long): Int =
    (maxHeapBytes / 3)
        .coerceAtMost(MAX_TARGET_BYTES)
        .coerceAtLeast(DefaultLoadControl.DEFAULT_MIN_BUFFER_SIZE.toLong())
        .toInt()

/**
 * The player's load control: the heap-bound byte budget, and on devices that are not low on RAM up to two minutes
 * ahead, so a network dip in a film never reaches the screen. Music (a few Mb/s at most) never meets the byte limit.
 * The byte limit keeps priority over the time minimum, and there is no back buffer: kept back-buffer bytes count
 * against the same budget and would stop loading ahead (Streamyfin's stall at 20 Mb/s).
 */
@OptIn(UnstableApi::class)
internal fun playerLoadControl(context: Context): LoadControl {
    val lowRam = context.getSystemService<ActivityManager>()?.isLowRamDevice == true
    return DefaultLoadControl
        .Builder()
        .setTargetBufferBytes(targetBufferBytes(Runtime.getRuntime().maxMemory()))
        .setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            if (lowRam) DefaultLoadControl.DEFAULT_MAX_BUFFER_MS else MAX_BUFFER_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        ).build()
}

private const val MAX_TARGET_BYTES = 384L * 1024 * 1024
private const val MAX_BUFFER_MS = 120_000
