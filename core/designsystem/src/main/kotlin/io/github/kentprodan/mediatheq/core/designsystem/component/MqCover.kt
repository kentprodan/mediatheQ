package io.github.kentprodan.mediatheq.core.designsystem.component

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.DataSource
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/**
 * Square cover art clipped to [shape]. Shows the [blurHash] placeholder (or a music note) until [model] loads, then
 * crossfades in with the theme's effects motion spec. [model] is anything Coil loads, or a ready [Painter]
 * (previews, screenshot tests). Never use `MaterialShapes` here (they crop the art).
 */
@Composable
fun MqCover(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    blurHash: String? = null,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val placeholder = remember(blurHash) { blurHash?.let(::blurHashBitmap) }
    // Crop here too, so Coil decodes at the size the cover is drawn (Fit would upscale and blur it).
    val asyncPainter = rememberAsyncImagePainter(model.takeUnless { it is Painter }, contentScale = ContentScale.Crop)
    val state by asyncPainter.state.collectAsState()
    val painter = model as? Painter ?: asyncPainter
    val success = state as? AsyncImagePainter.State.Success
    val loaded = model is Painter || success != null
    // Memory-cache hits (e.g. scrolling back in a grid) appear at once instead of fading in again.
    val instant = model is Painter || success?.result?.dataSource == DataSource.MEMORY_CACHE
    val alpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = if (instant) snap() else MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "coverAlpha",
    )
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (placeholder != null) {
            Image(placeholder, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (!loaded) {
            Icon(
                MqIcons.MusicNote,
                contentDescription = null,
                Modifier.fillMaxSize(PLACEHOLDER_ICON_FRACTION),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Image(
            painter,
            contentDescription,
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
            contentScale = ContentScale.Crop,
        )
    }
}

private const val PLACEHOLDER_ICON_FRACTION = 0.4f
private const val BLUR_HASH_SIZE = 32

private fun blurHashBitmap(hash: String): ImageBitmap? =
    decodeBlurHash(hash, BLUR_HASH_SIZE, BLUR_HASH_SIZE)?.let {
        Bitmap.createBitmap(it, BLUR_HASH_SIZE, BLUR_HASH_SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
