package io.github.kentprodan.mediatheq.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import io.github.kentprodan.mediatheq.core.common.formatDuration
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Chapter
import io.github.kentprodan.mediatheq.core.model.TrickplayInfo
import kotlin.time.Duration

/** The chapters sheet (#277): picture, name and time; a chapter seeks there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChaptersSheet(
    chapters: List<Chapter>,
    onChapter: (Chapter) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ChaptersContent(chapters) {
            onChapter(it)
            onDismiss()
        }
    }
}

@Composable
internal fun ChaptersContent(
    chapters: List<Chapter>,
    onChapter: (Chapter) -> Unit,
) {
    LazyColumn {
        items(chapters, key = { it.index }) { chapter ->
            ListItem(
                headlineContent = {
                    Text(chapter.name ?: stringResource(R.string.video_chapter_number, chapter.index + 1))
                },
                supportingContent = { Text(formatDuration(chapter.start)) },
                leadingContent = {
                    Box(
                        Modifier
                            .width(96.dp)
                            .aspectRatio(16 / 9f)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        chapter.imageUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop) }
                    }
                },
                modifier = Modifier.clickable { onChapter(chapter) },
            )
        }
    }
}

/**
 * The trickplay thumbnail of [at] (#277): its cell cropped out of the tile image, which is fetched only while dragging
 * (with the auth header), and the time under it.
 */
@Composable
internal fun TrickplayPreview(
    tiles: TrickplayInfo,
    at: Duration,
) {
    val cell = tiles.cellAt(at)
    val width = PREVIEW_WIDTH
    val height = PREVIEW_WIDTH * tiles.height / tiles.width.coerceAtLeast(1)
    val context = LocalContext.current
    Column(
        Modifier.padding(bottom = MediatheqSpacing.s).clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.xs),
    ) {
        Box(Modifier.size(width, height).clip(MaterialTheme.shapes.small).clipToBounds()) {
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
                    // Width only: the last tile image is just as tall as the rows it uses (review).
                    contentScale = ContentScale.FillWidth,
                    modifier =
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredWidth(width * tiles.tileWidth)
                            .offset(-width * cell.column, -height * cell.row),
                )
            }
        }
        Text(
            formatDuration(at),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val PREVIEW_WIDTH = 160.dp
