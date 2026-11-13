package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.common.QualityKind
import io.github.kentprodan.mediatheq.core.common.formatDuration
import io.github.kentprodan.mediatheq.core.common.qualityLabel
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.model.Track

/**
 * One track of a TV list (#238): its number (an equalizer icon while it plays), title, artists, an Atmos chip when
 * the file is Dolby Atmos, and the duration. Select plays it; [onLongClick] opens its menu.
 */
@Composable
fun MqTvTrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = track.trackNumber,
    artists: String? = track.artists.joinToString { it.name }.ifEmpty { null },
    isPlaying: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val playing = stringResource(R.string.core_ui_tv_playing)
    val quality = track.audioFormat?.let { qualityLabel(it, streamKnown = true, convertedKbps = null) }
    ListItem(
        selected = isPlaying,
        onClick = onClick,
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onLongClick = onLongClick,
        modifier = modifier.semantics { if (isPlaying) stateDescription = playing },
        leadingContent = {
            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    Icon(MqTvIcons.Playing, contentDescription = null, Modifier.size(20.dp))
                } else {
                    Text(
                        number?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                    )
                }
            }
        },
        supportingContent = artists?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (quality?.kind == QualityKind.DolbyAtmos) MqTvFormatBadge(quality)
                Text(formatDuration(track.duration), style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}
