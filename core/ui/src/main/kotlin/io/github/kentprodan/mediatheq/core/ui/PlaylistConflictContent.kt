package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.PlaylistChange
import io.github.kentprodan.mediatheq.core.model.PlaylistConflict
import io.github.kentprodan.mediatheq.core.model.PlaylistResolution
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.toJavaInstant

/**
 * The body of the playlist conflict dialog (#417), for an `MqDialog` titled [playlistConflictTitle]: what changed on
 * the server since the phone went offline, what waits on the phone, and the three answers stacked (Merge, Replace,
 * Skip). The dialog's own dismiss button is Later: it only hides the question until the next connection.
 */
@Composable
fun PlaylistConflictContent(
    conflict: PlaylistConflict,
    onResolve: (PlaylistResolution) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
    ) {
        val since =
            conflict.since?.let {
                DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withZone(zone)
                    .format(it.toJavaInstant())
            }
        Section(
            if (since == null) {
                stringResource(R.string.core_ui_conflict_server)
            } else {
                stringResource(R.string.core_ui_conflict_server_since, since)
            },
            describe(conflict.server),
        )
        Section(stringResource(R.string.core_ui_conflict_phone), describe(conflict.phone))
        Text(
            stringResource(R.string.core_ui_conflict_choices),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            TextButton(onClick = { onResolve(PlaylistResolution.Merge) }) {
                Text(stringResource(R.string.core_ui_conflict_merge))
            }
            TextButton(onClick = { onResolve(PlaylistResolution.Replace) }) {
                Text(stringResource(R.string.core_ui_conflict_replace))
            }
            TextButton(onClick = { onResolve(PlaylistResolution.Skip) }) {
                Text(stringResource(R.string.core_ui_conflict_skip))
            }
        }
    }
}

/** The dialog title for [conflict]. */
@Composable
fun playlistConflictTitle(conflict: PlaylistConflict): String =
    stringResource(R.string.core_ui_conflict_title, conflict.name)

@Composable
private fun Section(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "2 tracks added · 1 track removed · New order · Renamed to “Trip”". */
@Composable
private fun describe(change: PlaylistChange): String {
    val parts =
        buildList {
            if (change.added >
                0
            ) {
                add(pluralStringResource(R.plurals.core_ui_conflict_added, change.added, change.added))
            }
            if (change.removed > 0) {
                add(pluralStringResource(R.plurals.core_ui_conflict_removed, change.removed, change.removed))
            }
            if (change.reordered) add(stringResource(R.string.core_ui_conflict_reordered))
            change.renamedTo?.let { add(stringResource(R.string.core_ui_conflict_renamed, it)) }
        }
    return parts.joinToString(" · ").ifEmpty { stringResource(R.string.core_ui_conflict_nothing) }
}
