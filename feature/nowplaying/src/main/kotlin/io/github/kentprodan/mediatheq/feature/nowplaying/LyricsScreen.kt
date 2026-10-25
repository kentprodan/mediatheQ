package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqLoadingIndicator
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Lyrics
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * The playing track's lyrics, opened from Now Playing. Synced lyrics mark the line being sung and keep it in view; a
 * tap on a line plays from it when the stream can be seeked. A manual scroll stops the following for a few seconds.
 * [position] is read only to find the current line, so position ticks recompose only when the line changes.
 */
@Composable
fun LyricsScreen(
    state: LyricsUiState,
    position: () -> Duration,
    canSeek: Boolean,
    onAction: (NowPlayingAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.nowplaying_lyrics)
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().semantics { paneTitle = title }) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.nowplaying_lyrics_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            when (state) {
                LyricsUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MqLoadingIndicator() }
                }

                LyricsUiState.None -> {
                    MqEmptyState(
                        MqIcons.Lyrics,
                        stringResource(R.string.nowplaying_lyrics_none),
                        body = stringResource(R.string.nowplaying_lyrics_none_body),
                    )
                }

                is LyricsUiState.Error -> {
                    MqEmptyState(
                        MqIcons.CloudOff,
                        stringResource(R.string.nowplaying_lyrics_error),
                        body = stringResource(R.string.nowplaying_lyrics_error_body),
                        actionLabel = stringResource(R.string.nowplaying_lyrics_retry),
                        onAction = { onAction(NowPlayingAction.RetryLyrics) },
                    )
                }

                is LyricsUiState.Content -> {
                    LyricLines(state.lyrics, position, canSeek, onAction)
                }
            }
        }
    }
}

@Composable
private fun LyricLines(
    lyrics: Lyrics,
    position: () -> Duration,
    canSeek: Boolean,
    onAction: (NowPlayingAction) -> Unit,
) {
    val current by remember(lyrics) { derivedStateOf { lyrics.currentLineIndex(position()) } }
    // Starts at the sung line, which the top padding puts near the middle (also in previews, where effects do not run).
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    var following by remember { mutableStateOf(true) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) {
        if (dragged) {
            following = false
        } else if (!following) {
            delay(FOLLOW_PAUSE_MILLIS)
            following = true
        }
    }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(current, following) {
        if (following && current >= 0) listState.centerOn(current) { listState.animateScrollBy(it, spatial) }
    }
    val playFrom = stringResource(R.string.nowplaying_lyrics_play_from)
    val musicBreak = stringResource(R.string.nowplaying_lyrics_break)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Synced: half a screen above and below, so the first and last lines can be centered too.
        val vertical = if (lyrics.isSynced) maxHeight / 2 else MediatheqSpacing.xxl
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = MediatheqSpacing.l, vertical = vertical),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isCurrent = index == current
                val color by animateColorAsState(
                    when {
                        !lyrics.isSynced -> MaterialTheme.colorScheme.onSurface
                        isCurrent -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "line color",
                )
                val start = line.start
                Text(
                    // An empty synced line is an instrumental break.
                    line.text.ifBlank { MUSIC_BREAK },
                    // The current line is also bolder, so it is not marked by color alone.
                    style =
                        if (isCurrent) {
                            MaterialTheme.typography.headlineSmallEmphasized
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                    color = color,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (canSeek && start != null) {
                                    Modifier.clickable(onClickLabel = playFrom) {
                                        following = true
                                        onAction(NowPlayingAction.SeekToTime(start))
                                    }
                                } else {
                                    Modifier
                                },
                            ).padding(vertical = MediatheqSpacing.s)
                            .then(
                                if (line.text.isBlank()) {
                                    Modifier.semantics {
                                        contentDescription = musicBreak
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                )
            }
        }
    }
}

/** Scrolls so line [index] sits in the middle; jumps there first when it is out of view. */
private suspend fun LazyListState.centerOn(
    index: Int,
    scrollBy: suspend (Float) -> Unit,
) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
    scrollBy((item.offset + item.size / 2 - middle).toFloat())
}

private const val FOLLOW_PAUSE_MILLIS = 3_000L
private const val MUSIC_BREAK = "♪"
