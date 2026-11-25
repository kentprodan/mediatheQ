package io.github.kentprodan.mediatheq.feature.tv.video

import io.github.kentprodan.mediatheq.core.data.DisplayMatch
import kotlin.math.abs

/** A display mode of the TV (Android's `Display.Mode`, in plain values for the rules). */
data class TvDisplayMode(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

/**
 * The mode a video of [frameRate] (and [videoHeight]) should play in (#384), `null` for the viewer's own mode (off, no
 * rate known, or no mode fits: a mode chosen for the last video would be wrong for this one). [viewer] is the mode the
 * TV had before the player switched it, the resolution reference. Rules
 * (owner decision, after Moonfin's): a rate is snapped to the standard one it is near (1000/1001 rates told from whole
 * ones); the refresh must be a whole multiple of it (so 23.976 never plays at 60 Hz, which judders 3:2); 25 and 29.97
 * prefer twice their rate (50, 59.94) so a mix of 29.97 and 59.94 never switches; film keeps its own rate. With
 * [DisplayMatch.Rate] only modes of the current resolution count; with resolution, the smallest that fits the video.
 */
fun chooseDisplayMode(
    frameRate: Float,
    videoHeight: Int,
    modes: List<TvDisplayMode>,
    viewer: TvDisplayMode,
    match: DisplayMatch,
): TvDisplayMode? {
    if (match == DisplayMatch.Off || match == DisplayMatch.System || frameRate <= 0f) return null
    val rate = snapRate(frameRate)
    val sized =
        if (match == DisplayMatch.RateAndResolution) {
            val fitting = modes.filter { it.height >= videoHeight }.minOfOrNull { it.height } ?: viewer.height
            modes.filter { it.height == fitting }
        } else {
            modes.filter { it.width == viewer.width && it.height == viewer.height }
        }
    val preferredMultiples = if (rate in DOUBLED) listOf(2, 1) else listOf(1, 2, 3, 4, 5)
    return preferredMultiples.firstNotNullOfOrNull { multiple ->
        sized.filter { sameRate(it.refreshRate, rate * multiple) }.maxByOrNull { it.width }
    }
}

/**
 * Whether the display in [current] already shows [target]: asking for it would renegotiate HDMI (a black screen and
 * dropped audio) for nothing, so two videos that want the same mode keep it (#631).
 */
fun sameDisplayMode(
    target: TvDisplayMode,
    current: TvDisplayMode,
): Boolean =
    target.id == current.id || (sameRate(current.refreshRate, target.refreshRate) && target.height == current.height)

/** The standard rate [rate] is near (0.6 %), else [rate] as it is. */
internal fun snapRate(rate: Float): Float =
    STANDARD.minByOrNull { abs(it - rate) }?.takeIf { abs(it - rate) / it < SNAP } ?: rate

/** Within 0.1 %: 23.976 and 24 are different rates. */
private fun sameRate(
    a: Float,
    b: Float,
): Boolean = abs(a - b) / b < SAME

private val STANDARD = listOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f)
private val DOUBLED = setOf(25f, 29.97f)
private const val SNAP = 0.006f
private const val SAME = 0.001f

/** The display format chosen for [id] (#678), and whether the frames' own timing has been seen. */
internal data class DisplayLock(
    val id: io.github.kentprodan.mediatheq.core.model.MediaId,
    val format: Pair<Float, Int>,
    val measured: Boolean,
)

/**
 * The display format to keep (#678): once a video has one, only the first measurement of its frames may change it, and
 * only when it really differs (3:2 pulldown stated as 23.976 wants 59.94). A reload, a new track or a second
 * measurement keeps it, so the TV is never switched twice for one video. A new video starts over: its library rate,
 * else the measured or stated one.
 */
internal fun nextDisplayLock(
    previous: DisplayLock?,
    id: io.github.kentprodan.mediatheq.core.model.MediaId,
    height: Int,
    measured: Float?,
    library: Float?,
    stream: Float?,
): DisplayLock? {
    if (previous != null && previous.id == id) {
        if (previous.measured || measured == null) return previous
        val differs = kotlin.math.abs(measured - previous.format.first) > MEASURED_DIFFERS
        return previous.copy(
            format = if (differs) measured to previous.format.second else previous.format,
            measured = true,
        )
    }
    val rate = library ?: measured ?: stream ?: return null
    return DisplayLock(id, rate to height, measured = measured != null)
}

// A real cadence change (24 to 60) is far above this; 23.976 measured as 23.98 is not one.
private const val MEASURED_DIFFERS = 0.5f
