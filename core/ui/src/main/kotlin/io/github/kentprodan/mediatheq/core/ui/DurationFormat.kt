package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.common.TimeAgoUnit
import io.github.kentprodan.mediatheq.core.common.timeAgo
import kotlin.time.Duration

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

/** "Just now", "5 minutes ago", "3 hours ago", "2 days ago": how long ago, in its largest whole unit. */
@Composable
fun formatTimeAgo(elapsed: Duration): String {
    val (unit, count) = timeAgo(elapsed)
    return when (unit) {
        TimeAgoUnit.JustNow -> stringResource(R.string.core_ui_just_now)
        TimeAgoUnit.Minutes -> pluralStringResource(R.plurals.core_ui_minutes_ago, count, count)
        TimeAgoUnit.Hours -> pluralStringResource(R.plurals.core_ui_hours_ago, count, count)
        TimeAgoUnit.Days -> pluralStringResource(R.plurals.core_ui_days_ago, count, count)
    }
}
