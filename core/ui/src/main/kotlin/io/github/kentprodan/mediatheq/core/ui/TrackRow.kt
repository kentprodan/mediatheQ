package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.Track

/**
 * One track in a list: optional track number or cover, title, artists, duration and an overflow button.
 * TalkBack reads the row as one item ("title, artists, duration"); the overflow button stays separately focusable.
 * The playing track ([isCurrent]) shows an equalizer icon instead of its number or cover, a primary-colored title and
 * "Now playing" for TalkBack, so color is never the only signal.
 */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTrackNumber: Boolean = true,
    showCover: Boolean = false,
    /** The overflow menu's items (see [OverflowMenu]); no button when `null`. */
    menuItems: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    isCurrent: Boolean = false,
    /** Long press, e.g. to start selecting; `null` for none. */
    onLongClick: (() -> Unit)? = null,
    /** In a multi-select list: whether this row is selected (shown with a check). `null` outside selection. */
    selected: Boolean? = null,
) {
    val trackNumber = track.trackNumber.takeIf { showTrackNumber && !showCover }
    val nowPlaying = stringResource(R.string.core_ui_now_playing)
    val rowModifier = if (isCurrent) modifier.semantics { stateDescription = nowPlaying } else modifier
    val leading: (@Composable () -> Unit)? =
        if (selected == true) {
            { Icon(MqIcons.CheckCircleFilled, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else if (isCurrent && trackNumber != null) {
            {
                // Sized like the two-digit number cell, so the titles stay aligned.
                Box(Modifier.clearAndSetSemantics {}) {
                    Text("00", Modifier.alpha(0f), style = MaterialTheme.typography.bodyMedium)
                    Icon(
                        MqIcons.GraphicEq,
                        null,
                        Modifier.matchParentSize(),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else if (isCurrent) {
            { Icon(MqIcons.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else if (showCover) {
            { RowCover(track.artwork, MqIcons.MusicNote) }
        } else {
            trackNumber?.let {
                {
                    // An invisible "00" reserves two digits of width, so titles line up from track 1 to 99.
                    Box(Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.CenterEnd) {
                        Text("00", Modifier.alpha(0f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            it.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    val supporting: @Composable () -> Unit = {
        Text(track.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val trailing: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(track.duration), style = MaterialTheme.typography.bodySmall)
            if (menuItems != null) {
                OverflowMenu(stringResource(R.string.core_ui_more_options, track.title), items = menuItems)
            }
        }
    }
    val title: @Composable () -> Unit = {
        Text(
            track.title,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (selected != null) {
        ListItem(
            selected = selected,
            onClick = onClick,
            modifier = rowModifier,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            onLongClick = onLongClick,
            content = title,
        )
    } else {
        ListItem(
            onClick = onClick,
            modifier = rowModifier,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            onLongClick = onLongClick,
            content = title,
        )
    }
}
