package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import io.github.kentprodan.mediatheq.core.model.TrickplayInfo
import kotlin.time.Duration

/**
 * The seek preview on TV (#430, as the phone's #277): the frame at [at], cropped out of its trickplay tile image (fetched
 * with the auth header the route needs).
 */
@Composable
fun MqTvTrickplayPreview(
    tiles: TrickplayInfo,
    at: Duration,
    modifier: Modifier = Modifier,
) {
    val cell = tiles.cellAt(at)
    val width = PREVIEW_WIDTH
    val height = PREVIEW_WIDTH * tiles.height / tiles.width.coerceAtLeast(1)
    val context = LocalContext.current
    Box(modifier.size(width, height).clip(MaterialTheme.shapes.small).clipToBounds()) {
        tiles.tileUrls.getOrNull(cell.tile)?.let { url ->
            AsyncImage(
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .httpHeaders(
                        NetworkHeaders
                            .Builder()
                            .apply {
                                tiles.headers.forEach { (k, v) ->
                                    set(k, v)
                                }
                            }.build(),
                    ).build(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier =
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredWidth(width * tiles.tileWidth)
                        .offset(-width * cell.column, -height * cell.row),
            )
        }
    }
}

private val PREVIEW_WIDTH = 240.dp
