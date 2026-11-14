package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.common.formatDuration
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Video
import kotlin.time.Duration

/**
 * An episode in a season's list (#273): its 16:9 still with the progress line along the bottom while it is halfway,
 * "4. Title", the runtime, the watched check and the downloaded mark, and [trailing] (its menu). A tap plays it; a long
 * press opens [onLongClick] (the menu).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MqEpisodeRow(
    episode: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    downloaded: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val watched = episode.userData.played
    val progress =
        episode.userData.playbackPosition
            .takeIf { !watched && it > Duration.ZERO && episode.duration > Duration.ZERO }
            ?.let { (it / episode.duration).toFloat().coerceIn(0f, 1f) }
    val watchedLabel = stringResource(R.string.core_ui_watched)
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { if (watched) stateDescription = watchedLabel }
            .padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(ThumbWidth)) {
            MqCover(episode.artwork, contentDescription = null, aspectRatio = 16 / 9f)
            progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(MediatheqSpacing.xs),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(episode.episodeNumber?.let { "$it." }, episode.title).joinToString(" "),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (episode.duration > Duration.ZERO) {
                    Text(
                        formatDuration(episode.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (watched) {
                    Icon(
                        MqIcons.Check,
                        contentDescription = null,
                        Modifier.size(MarkSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (downloaded) {
                    Icon(
                        MqIcons.DownloadForOffline,
                        contentDescription = stringResource(R.string.core_ui_downloaded),
                        modifier = Modifier.size(MarkSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        trailing()
    }
}

private val ThumbWidth = 144.dp
private val MarkSize = 16.dp
