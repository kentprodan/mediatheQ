package io.github.kentprodan.mediatheq.core.ui

import kotlin.time.Duration

/** Formats a track or album length as `m:ss`, or `h:mm:ss` from one hour. Locale-independent digits. */
fun formatDuration(duration: Duration): String =
    duration.toComponents { hours, minutes, seconds, _ ->
        val ss = seconds.toString().padStart(2, '0')
        if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
    }
