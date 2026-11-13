package io.github.kentprodan.mediatheq.core.common

import kotlin.time.Duration

/** Formats a track or album length as `m:ss`, or `h:mm:ss` from one hour. Locale-independent digits. */
fun formatDuration(duration: Duration): String =
    duration.toComponents { hours, minutes, seconds, _ ->
        val ss = seconds.toString().padStart(2, '0')
        if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
    }

/** The largest whole unit of a "time ago" text. */
enum class TimeAgoUnit { JustNow, Minutes, Hours, Days }

/** Under a minute (also a clock set back) is just now. */
fun timeAgo(elapsed: Duration): Pair<TimeAgoUnit, Int> =
    when {
        elapsed.inWholeMinutes < 1 -> TimeAgoUnit.JustNow to 0
        elapsed.inWholeHours < 1 -> TimeAgoUnit.Minutes to elapsed.inWholeMinutes.toInt()
        elapsed.inWholeDays < 1 -> TimeAgoUnit.Hours to elapsed.inWholeHours.toInt()
        else -> TimeAgoUnit.Days to elapsed.inWholeDays.toInt()
    }
