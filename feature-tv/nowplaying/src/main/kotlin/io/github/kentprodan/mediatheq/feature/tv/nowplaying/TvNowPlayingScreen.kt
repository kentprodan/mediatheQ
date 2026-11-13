package io.github.kentprodan.mediatheq.feature.tv.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.common.formatDuration
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvListItem
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMessage
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvCover
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvFormatBadge
import kotlin.time.Duration

@Composable
fun TvNowPlayingRoute(viewModel: TvNowPlayingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    TvNowPlayingScreen(state, position, viewModel::onAction, lyrics = lyrics)
}

/**
 * The full-screen player on TV (#240): the cover and what plays, the progress row (Left / Right seek), the controls,
 * and the queue on the right (select jumps, long-press removes), or the [lyrics] in its place (#245: the Lyrics
 * button opens them, Back closes them and returns to the button). Focus starts on Play / Pause. The screen stays on
 * while music plays here. The remote's media keys reach the session directly (media3 skill, section 11).
 */
@Composable
fun TvNowPlayingScreen(
    state: TvNowPlayingUiState,
    position: Duration,
    onAction: (TvNowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
    lyrics: TvLyricsUiState = TvLyricsUiState.Loading,
    lyricsOpen: Boolean = false,
) {
    var showLyrics by rememberSaveable { mutableStateOf(lyricsOpen) }
    val lyricsButton = remember { FocusRequester() }
    // Open while the next track's lyrics load too, so a track change does not swap the queue in and out.
    val lyricsShown = showLyrics && (lyrics is TvLyricsUiState.Content || lyrics == TvLyricsUiState.Loading)
    BackHandler(enabled = lyricsShown) {
        showLyrics = false
        runCatching { lyricsButton.requestFocus() }
    }
    // A new track's lyrics replace the lines that may have had focus: it waits on the Lyrics button. A track without
    // lyrics closes the panel (the button is then off: focus moves on along the controls by itself).
    LaunchedEffect(lyrics) {
        if (!showLyrics || lyrics is TvLyricsUiState.Content) return@LaunchedEffect
        if (lyrics == TvLyricsUiState.None) showLyrics = false
        runCatching { lyricsButton.requestFocus() }
    }
    when (state) {
        TvNowPlayingUiState.Idle -> {
            MqTvMessage(
                stringResource(R.string.tv_np_nothing_title),
                stringResource(R.string.tv_np_nothing_body),
                modifier,
            )
        }

        is TvNowPlayingUiState.Playing -> {
            KeepScreenOn(state.isPlaying)
            Row(modifier.fillMaxSize()) {
                val lyricsControl =
                    LyricsControl(lyrics, lyricsButton) {
                        if (lyrics ==
                            TvLyricsUiState.Error
                        ) {
                            onAction(TvNowPlayingAction.RetryLyrics)
                        } else {
                            showLyrics =
                                !showLyrics
                        }
                    }
                Player(state, position, onAction, Modifier.weight(PLAYER_WEIGHT), lyricsControl)
                if (lyricsShown) {
                    val content = (lyrics as? TvLyricsUiState.Content)?.lyrics
                    // Loading the next track's lyrics: the panel stays, empty.
                    if (content != null) {
                        TvLyricsPanel(content, position, Modifier.weight(1 - PLAYER_WEIGHT))
                    } else {
                        Spacer(Modifier.weight(1 - PLAYER_WEIGHT))
                    }
                } else {
                    Queue(state, onAction, Modifier.weight(1 - PLAYER_WEIGHT))
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun Player(
    state: TvNowPlayingUiState.Playing,
    position: Duration,
    onAction: (TvNowPlayingAction) -> Unit,
    modifier: Modifier,
    lyrics: LyricsControl,
) {
    val playPause = remember { FocusRequester() }
    Column(
        modifier.fillMaxHeight().padding(start = EDGE, end = 24.dp, top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Bottom) {
            MqTvCover(state.artwork, MqTvIcons.MusicNote, Modifier.size(COVER).clip(MaterialTheme.shapes.medium))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    state.title,
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.artist,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.album?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The output line: the quality, and why an Atmos file is not Atmos on this output.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.quality?.let { MqTvFormatBadge(it) }
                    val atmosNote =
                        when {
                            state.atmosConverted -> R.string.tv_np_atmos_converted
                            state.atmosDecoded -> R.string.tv_np_atmos_decoded
                            else -> null
                        }
                    if (atmosNote != null) {
                        Text(
                            stringResource(atmosNote),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Progress(state, position, onAction)
        Controls(state, onAction, playPause, lyrics)
        // Why the Lyrics button is off: the track has none, or the server could not be asked.
        lyrics.hint?.let {
            Text(
                stringResource(it),
                Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { playPause.requestFocus() } }
}

/** The progress row: focusable, Left / Right seek (faster while held); the times under the bar. */
@Composable
private fun Progress(
    state: TvNowPlayingUiState.Playing,
    position: Duration,
    onAction: (TvNowPlayingAction) -> Unit,
) {
    val label = stringResource(R.string.tv_np_progress)
    val fraction = if (state.duration.isPositive()) (position / state.duration).toFloat().coerceIn(0f, 1f) else 0f
    Surface(
        onClick = {},
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    stateDescription = "${formatDuration(position)} / ${formatDuration(state.duration)}"
                }.onPreviewKeyEvent { event ->
                    // Not seekable (a live transcode): Left and Right move focus as anywhere else.
                    if (!state.isSeekable) return@onPreviewKeyEvent false
                    val forward =
                        when (event.key) {
                            Key.DirectionRight -> true
                            Key.DirectionLeft -> false
                            else -> return@onPreviewKeyEvent false
                        }
                    if (event.type == KeyEventType.KeyDown) {
                        onAction(TvNowPlayingAction.Seek(forward, event.nativeKeyEvent.repeatCount))
                    }
                    true
                },
        enabled = state.isSeekable,
        colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(MaterialTheme.colorScheme.primary))
            }
            Row {
                Text(formatDuration(position), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                if (state.isBuffering) {
                    Text(stringResource(R.string.tv_np_buffering), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                }
                Text(formatDuration(state.duration), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun Controls(
    state: TvNowPlayingUiState.Playing,
    onAction: (TvNowPlayingAction) -> Unit,
    playPause: FocusRequester,
    lyrics: LyricsControl,
) {
    val on = stringResource(R.string.tv_np_on)
    val off = stringResource(R.string.tv_np_off)
    Row(
        Modifier.fillMaxWidth().focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Control(
            MqTvIcons.Shuffle,
            stringResource(R.string.tv_np_shuffle),
            if (state.shuffle) on else off,
            selected = state.shuffle,
        ) {
            onAction(TvNowPlayingAction.ToggleShuffle)
        }
        Control(MqTvIcons.SkipPrevious, stringResource(R.string.tv_np_previous), enabled = state.hasPrevious) {
            onAction(TvNowPlayingAction.Previous)
        }
        Control(
            if (state.isPlaying) MqTvIcons.Pause else MqTvIcons.Play,
            stringResource(if (state.isPlaying) R.string.tv_np_pause else R.string.tv_np_play),
            modifier = Modifier.focusRequester(playPause),
            large = true,
        ) { onAction(TvNowPlayingAction.TogglePlayPause) }
        Control(MqTvIcons.SkipNext, stringResource(R.string.tv_np_next), enabled = state.hasNext) {
            onAction(TvNowPlayingAction.Next)
        }
        val repeat =
            stringResource(
                when (state.repeatMode) {
                    RepeatMode.Off -> R.string.tv_np_repeat_off
                    RepeatMode.All -> R.string.tv_np_repeat_all
                    RepeatMode.One -> R.string.tv_np_repeat_one
                },
            )
        Control(
            if (state.repeatMode == RepeatMode.One) MqTvIcons.RepeatOne else MqTvIcons.Repeat,
            stringResource(R.string.tv_np_repeat),
            repeat,
            selected = state.repeatMode != RepeatMode.Off,
        ) { onAction(TvNowPlayingAction.CycleRepeat) }
        Control(
            if (state.isFavorite) MqTvIcons.FavoriteFilled else MqTvIcons.Favorite,
            stringResource(R.string.tv_np_favorite),
            if (state.isFavorite) on else off,
            selected = state.isFavorite,
        ) { onAction(TvNowPlayingAction.ToggleFavorite) }
        Control(
            MqTvIcons.Lyrics,
            stringResource(R.string.tv_np_lyrics),
            modifier = Modifier.focusRequester(lyrics.focus),
            enabled = lyrics.available,
            onClick = lyrics.onToggle,
        )
    }
}

/**
 * The Lyrics button: opens the panel when the track has lyrics (or they are loading), asks again after an error, off
 * when the track has none; [hint] says why under the controls.
 */
private class LyricsControl(
    lyrics: TvLyricsUiState,
    val focus: FocusRequester,
    val onToggle: () -> Unit,
) {
    val available = lyrics != TvLyricsUiState.None
    val hint: Int? =
        when (lyrics) {
            TvLyricsUiState.None -> R.string.tv_np_lyrics_none
            TvLyricsUiState.Error -> R.string.tv_np_lyrics_error
            else -> null
        }
}

/** A round icon button; [stateText] is what TalkBack says of a toggle ("On"), and [selected] tints a toggle that is on. */
@Composable
private fun Control(
    icon: ImageVector,
    label: String,
    stateText: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(
        onClick,
        modifier
            .size(if (large) 72.dp else 56.dp)
            .semantics { stateText?.let { stateDescription = it } },
        enabled = enabled,
    ) {
        Icon(
            icon,
            contentDescription = label,
            Modifier.size(if (large) 40.dp else 28.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
        )
    }
}

/** Up next: the queue in play order; select jumps there, long-press (or the menu key) offers Remove. */
@Composable
private fun Queue(
    state: TvNowPlayingUiState.Playing,
    onAction: (TvNowPlayingAction) -> Unit,
    modifier: Modifier,
) {
    var menu by remember { mutableStateOf<QueueItem?>(null) }
    val focus = rememberFocusMemory()
    // A removed row had focus: the row now in its place takes it.
    var removedAt by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.queue) {
        val index = removedAt ?: return@LaunchedEffect
        removedAt = null
        withFrameNanos {}
        state.queue.getOrNull(index.coerceAtMost(state.queue.lastIndex))?.let { focus.focus(it.entryId) }
    }
    Column(
        modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Text(
            stringResource(R.string.tv_np_queue),
            Modifier.padding(start = 24.dp, top = 40.dp, bottom = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        LazyColumn(
            Modifier.fillMaxWidth().focusRestorer(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.queue, key = { it.entryId }) { item ->
                val current = item.entryId == state.currentEntryId
                MqTvListItem(
                    item.title,
                    { onAction(TvNowPlayingAction.SkipTo(item.entryId)) },
                    Modifier
                        .width(QUEUE_ROW)
                        .focusMemory(focus, item.entryId)
                        .onPreviewKeyEvent { event ->
                            // The remote's menu key does what a long-press does; the playing entry has no menu.
                            val isMenu = event.key == Key.Menu && !current
                            if (isMenu && event.type == KeyEventType.KeyUp) menu = item
                            isMenu
                        },
                    supporting = item.artist,
                    icon = if (current) MqTvIcons.Playing else null,
                    selected = current,
                    onLongClick = if (current) null else ({ menu = item }),
                )
            }
        }
    }
    menu?.let { item ->
        MqTvMenu(
            item.title,
            listOf(
                MqTvMenuEntry(stringResource(R.string.tv_np_remove), null) {
                    onAction(TvNowPlayingAction.Remove(item.entryId))
                },
            ),
            onDismiss = { menu = null },
        )
    }
}

private val EDGE = 48.dp
private val COVER = 240.dp
private val QUEUE_ROW = 360.dp
private const val PLAYER_WEIGHT = 0.62f
