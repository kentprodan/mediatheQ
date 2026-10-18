package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.Track

/**
 * One track in a list: optional track number, title, artists, duration and an overflow button.
 * TalkBack reads the row as one item ("title, artists, duration"); the overflow button stays separately focusable.
 */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTrackNumber: Boolean = true,
) {
    val trackNumber = track.trackNumber.takeIf { showTrackNumber }
    ListItem(
        onClick = onClick,
        modifier = modifier,
        leadingContent =
            trackNumber?.let {
                {
                    Text(
                        it.toString(),
                        Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        supportingContent = {
            Text(track.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(track.duration), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = onMoreClick) {
                    Icon(
                        MqIcons.MoreVert,
                        contentDescription = stringResource(R.string.core_ui_more_options, track.title),
                    )
                }
            }
        },
    ) {
        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
