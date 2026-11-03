package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** One cover of an [MqCoverCarousel]. */
data class MqCarouselItem(
    val key: Any,
    val cover: Any?,
    val title: String,
    val subtitle: String?,
    val blurHash: String? = null,
)

/**
 * Large covers in an M3 multi-browse carousel (#127, Home's Carousel display): the title and subtitle over the bottom of
 * each cover on a scrim, so they stay readable on any art. A tap opens the item.
 */
@Composable
fun MqCoverCarousel(
    items: List<MqCarouselItem>,
    onClick: (MqCarouselItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberCarouselState { items.size }
    val shape = MaterialTheme.shapes.extraLarge
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = MediatheqSizes.carouselItem,
        modifier = modifier.fillMaxWidth().height(MediatheqSizes.carouselItem),
        itemSpacing = MediatheqSpacing.s,
        contentPadding = PaddingValues(horizontal = MediatheqSpacing.l),
    ) { index ->
        val item = items[index]
        Box(
            Modifier
                .fillMaxSize()
                .maskClip(shape)
                .clickable { onClick(item) }
                .semantics(mergeDescendants = true) {},
        ) {
            MqCover(item.cover, contentDescription = null, Modifier.fillMaxSize(), item.blurHash, shape)
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    // Scrim over the art: the text is white on any cover.
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM_ALPHA))),
                    ).padding(MediatheqSpacing.m),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val SCRIM_ALPHA = 0.6f
