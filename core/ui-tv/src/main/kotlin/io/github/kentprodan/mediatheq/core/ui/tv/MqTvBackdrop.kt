package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import io.github.kentprodan.mediatheq.core.model.Artwork

/**
 * A wide picture behind a page's header (#239, an artist's backdrop, R21), faded into the background at the bottom
 * and the start so text on it stays readable. Nothing but the background without [artwork].
 */
@Composable
fun MqTvBackdrop(
    artwork: Artwork?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier) {
        if (artwork != null) {
            AsyncImage(artwork, contentDescription = null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.horizontalGradient(listOf(background, background.copy(alpha = 0.4f))))
                    .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background))),
            )
        }
        content()
    }
}
