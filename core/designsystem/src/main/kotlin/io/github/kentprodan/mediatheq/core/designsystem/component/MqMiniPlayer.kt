package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.designsystem.R
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** How the mini player shows the position (#126). */
enum class MqMiniProgress { Hidden, Line, Background }

/**
 * The mini player (expressive-design skill, section 5): cover, title and artist, play/pause and next. Tapping it opens
 * the full player. [coverModifier] lets the caller fly the cover into Now Playing. A floating pill, or [docked] full
 * width on the bottom bar (top corners rounded). With [progressStyle] Line a thin line on the bottom edge, with
 * Background the container fills as the track plays; [progress] (0..1) is read in the draw phase only, so position
 * ticks never recompose the player.
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
    docked: Boolean = false,
    progressStyle: MqMiniProgress = MqMiniProgress.Hidden,
    progress: () -> Float = { 0f },
) {
    val openLabel = stringResource(R.string.mq_open_player)
    val shape =
        if (docked) {
            MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0))
        } else {
            CircleShape
        }
    // A tonal step over the container: text keeps its contrast on both parts.
    val fill = MaterialTheme.colorScheme.secondaryContainer
    val line = MaterialTheme.colorScheme.primary
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClickLabel = openLabel, onClick = onOpen),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier
                .drawWithContent {
                    val played = size.width * progress().coerceIn(0f, 1f)
                    if (progressStyle == MqMiniProgress.Background) drawRect(fill, size = Size(played, size.height))
                    drawContent()
                    if (progressStyle == MqMiniProgress.Line) {
                        val height = LineHeight.toPx()
                        drawRect(line, Offset(0f, size.height - height), Size(played, height))
                    }
                }.padding(MediatheqSpacing.s),
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
private val LineHeight = 3.dp
