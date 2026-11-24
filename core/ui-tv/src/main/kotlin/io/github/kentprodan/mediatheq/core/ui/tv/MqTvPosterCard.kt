package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvCard
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvCardStyle
import io.github.kentprodan.mediatheq.core.model.Artwork

/**
 * A 2:3 poster card (#426): a movie or a series with its [title] and [subtitle] (the year). A watched one carries a
 * check in its corner; a started one ([progress] 0-1) a bar along its bottom edge.
 */
@Composable
fun MqTvPosterCard(
    title: String,
    artwork: Artwork?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    watched: Boolean = false,
    progress: Float? = null,
    placeholder: androidx.compose.ui.graphics.vector.ImageVector = MqTvIcons.Movie,
    /** 16:9 for an episode's still. */
    aspectRatio: Float = POSTER_RATIO,
    onLongClick: (() -> Unit)? = null,
    /** The guideline card variant (#518). */
    style: MqTvCardStyle = MqTvCardStyle.Classic,
    /** The wide variants' two lines under the title (#603). */
    description: String? = null,
) {
    MqTvCard(
        title,
        onClick,
        modifier,
        subtitle,
        onLongClick,
        imageAspectRatio = aspectRatio,
        style = style,
        description = description,
    ) {
        Box(Modifier.fillMaxSize()) {
            MqTvCover(artwork, placeholder)
            if (watched) {
                Icon(
                    MqTvIcons.Watched,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.extraLarge),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            progress?.takeIf { it > 0f && !watched }?.let { fraction ->
                val track = MaterialTheme.colorScheme.surfaceVariant
                val bar = MaterialTheme.colorScheme.primary
                // Drawn in the draw phase: no layout per poster for a value that only paints.
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).drawBehind {
                        drawRect(track)
                        drawRect(bar, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
                    },
                )
            }
        }
    }
}

/** Posters are 2:3 (Jellyfin's Primary image of a movie or series). */
const val POSTER_RATIO = 2f / 3f
