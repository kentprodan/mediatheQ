package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlin.time.Duration

/** Formats a track or album length as `m:ss`, or `h:mm:ss` from one hour. Locale-independent digits. */
fun formatDuration(duration: Duration): String =
    duration.toComponents { hours, minutes, seconds, _ ->
        val ss = seconds.toString().padStart(2, '0')
        if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
    }

/** A total length for headers: "45 min", or "1 h 12 min" from one hour. */
@Composable
fun formatTotalDuration(duration: Duration): String {
    val minutes = duration.inWholeMinutes.toInt()
    return if (minutes >= 60) {
        stringResource(R.string.core_ui_duration_hours, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.core_ui_duration_minutes, minutes)
    }
}

/** "11 tracks". */
@Composable
fun formatTrackCount(count: Int): String = pluralStringResource(R.plurals.core_ui_track_count, count, count)
