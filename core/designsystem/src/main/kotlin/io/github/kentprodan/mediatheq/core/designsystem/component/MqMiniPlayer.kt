package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.designsystem.R
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/**
 * The floating mini player pill (expressive-design skill, section 5): cover, title and artist, play/pause and next.
 * Tapping the pill opens the full player. [coverModifier] lets the caller fly the cover into Now Playing.
 */
@Composable
fun MqMiniPlayer(
    title: String,
    artist: String,
    cover: Any?,
    isPlaying: Boolean,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    coverBlurHash: String? = null,
    coverModifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.mq_open_player)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .clickable(onClickLabel = openLabel, onClick = onOpen),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier.padding(MediatheqSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
        ) {
            MqCover(
                model = cover,
                contentDescription = null,
                modifier = coverModifier.size(CoverSize),
                blurHash = coverBlurHash,
                shape = CircleShape,
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) MqIcons.PauseFilled else MqIcons.PlayFilled,
                    contentDescription = stringResource(if (isPlaying) R.string.mq_pause else R.string.mq_play),
                )
            }
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(MqIcons.SkipNextFilled, contentDescription = stringResource(R.string.mq_next_track))
            }
        }
    }
}

private val CoverSize = 48.dp
