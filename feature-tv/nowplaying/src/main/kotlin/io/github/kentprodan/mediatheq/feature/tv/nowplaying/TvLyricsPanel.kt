package io.github.kentprodan.mediatheq.feature.tv.nowplaying

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.model.Lyrics
import kotlin.time.Duration

/**
 * The lyrics in the queue's place (#245). Synced: the line sung at [position] large and centered, the others dimmed,
 * scrolling with playback; nothing here takes focus, so the transport controls keep it. Plain: one focusable block per
 * line, so D-pad Right enters and Down scrolls the text. Back closes the panel (the screen's handler).
 */
@Composable
fun TvLyricsPanel(
    lyrics: Lyrics,
    position: Duration,
    modifier: Modifier = Modifier,
) {
    // The ticking position only reaches the lines when the sung line changes.
    val latest = rememberUpdatedState(position)
    val current by remember(lyrics) { derivedStateOf { lyrics.currentLineIndex(latest.value) } }
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    if (lyrics.isSynced) {
        LaunchedEffect(current) { if (current >= 0) listState.centerOn(current) }
    }
    BoxWithConstraints(modifier.fillMaxHeight()) {
        // Synced: half the panel above and below, so the first and last lines can be centered too.
        val top = if (lyrics.isSynced) maxHeight / 2 else 40.dp
        LazyColumn(
            Modifier.fillMaxHeight().focusProperties { canFocus = !lyrics.isSynced },
            state = listState,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp, top = top, bottom = maxHeight / 2),
            verticalArrangement = Arrangement.spacedBy(if (lyrics.isSynced) 16.dp else 4.dp),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                // An empty synced line is an instrumental break.
                val text = line.text.ifBlank { MUSIC_BREAK }
                if (lyrics.isSynced) {
                    val isCurrent = index == current
                    Text(
                        text,
                        Modifier.fillMaxWidth(),
                        // The sung line is also larger, so it is not marked by color alone.
                        style = if (isCurrent) typography.headlineMedium else typography.titleLarge,
                        color = if (isCurrent) colors.primary else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    // Focusable only to scroll with the D-pad: select does nothing.
                    Surface(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = colors.surface,
                                focusedContainerColor = colors.surfaceVariant,
                            ),
                    ) {
                        Text(
                            text,
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

private const val MUSIC_BREAK = "♪"

/** Scrolls so line [index] sits in the middle of the list (its own height included, also for a wrapped line). */
private suspend fun LazyListState.centerOn(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val middle = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    animateScrollBy((item.offset + item.size / 2 - middle).toFloat())
}
