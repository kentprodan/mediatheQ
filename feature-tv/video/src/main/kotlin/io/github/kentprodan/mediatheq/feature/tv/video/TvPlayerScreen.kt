package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.common.formatDuration
import io.github.kentprodan.mediatheq.core.data.VideoTracks
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMessage
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvSubtitles
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvVideoSurface
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun TvPlayerRoute(
    key: VideoPlayerKey,
    onBack: () -> Unit,
) {
    val viewModel =
        hiltViewModel<TvPlayerViewModel, TvPlayerViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.videoPlayer.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val nextIn by viewModel.nextIn.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == TvPlayerUiState.Ended) onBack() }
    TvPlayerScreen(
        state,
        position,
        nextIn,
        viewModel::onAction,
        onLeave = {
            viewModel.onAction(TvPlayerAction.Leave)
            onBack()
        },
    ) {
        MqTvVideoSurface(player, Modifier.fillMaxSize())
        MqTvSubtitles(player, Modifier.fillMaxSize())
    }
}

/**
 * The TV video player (#429). Hidden controls: Left and Right seek at once (held, faster), Select or Up or Down shows
 * the bar, Play/Pause toggles. The bar (title, progress with the end time, Play/Pause, -10 s, +30 s, Audio & subtitles,
 * Next episode) hides 5 s after the last key. Paused, the title and overview show over a dimmed picture. Back hides
 * the bar first, then leaves (the position is saved by the session).
 */
@Composable
fun TvPlayerScreen(
    state: TvPlayerUiState,
    position: Duration,
    nextIn: Int?,
    onAction: (TvPlayerAction) -> Unit,
    onLeave: () -> Unit,
    surface: @Composable () -> Unit,
) {
    var bar by remember { mutableStateOf(false) }
    // Open over the bar: the bar waits for it (its keys never reach this screen's handler).
    var tracks by remember { mutableStateOf(false) }
    var keys by remember { mutableIntStateOf(0) }
    var seeking by remember { mutableIntStateOf(0) }
    val root = remember { FocusRequester() }
    BackHandler {
        if (bar) bar = false else onLeave()
    }
    LaunchedEffect(bar, keys, tracks) {
        if (bar && !tracks) {
            delay(BAR_TIME)
            bar = false
        }
    }
    LaunchedEffect(seeking) {
        if (seeking > 0) {
            delay(SEEK_TIME)
            seeking = 0
        }
    }
    // Back on the picture when the bar or the next-episode card goes, so the D-pad seeks again.
    LaunchedEffect(bar, nextIn == null) { if (!bar && nextIn == null) runCatching { root.requestFocus() } }
    // A movie never meets the screensaver; paused, the screen may sleep.
    KeepScreenOn((state as? TvPlayerUiState.Playing)?.isPlaying == true)
    val playing = state as? TvPlayerUiState.Playing
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(root)
            .onPreviewKeyEvent { event ->
                keys++
                // The bar's buttons and the next-episode card take their own keys.
                if (bar || nextIn != null) return@onPreviewKeyEvent false
                val down = event.type == KeyEventType.KeyDown
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (down) {
                            onAction(
                                TvPlayerAction.Seek(event.key == Key.DirectionRight, event.nativeKeyEvent.repeatCount),
                            )
                            seeking++
                        }
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.DirectionUp, Key.DirectionDown -> {
                        if (!down) bar = true
                        true
                    }

                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (down) onAction(TvPlayerAction.TogglePlayPause)
                        true
                    }

                    else -> {
                        false
                    }
                }
            }.focusable(),
    ) {
        surface()
        when (state) {
            is TvPlayerUiState.Error -> {
                Surface(Modifier.fillMaxSize()) {
                    MqTvMessage(stringResource(R.string.tv_player_error), stringResource(R.string.tv_player_error_body))
                }
            }

            is TvPlayerUiState.Playing -> {
                if (!state.isPlaying && !state.isBuffering && !bar) PauseInfo(state.video)
                if (bar) {
                    Bar(state, position, onAction) { tracks = true }
                } else if (seeking > 0) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(EDGE)) { Progress(position, state.duration) }
                }
                nextIn?.let { NextCard(it, onAction, Modifier.align(Alignment.BottomEnd)) }
            }

            TvPlayerUiState.Loading, TvPlayerUiState.Ended -> {
                Unit
            }
        }
    }
    playing?.tracks?.takeIf { tracks }?.let { PlayerTracksMenu(it, onAction) { tracks = false } }
    if (playing == null) LaunchedEffect(Unit) { runCatching { root.requestFocus() } }
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
private fun PauseInfo(video: Video) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        Column(Modifier.align(Alignment.BottomStart).padding(EDGE), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titleOf(video), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            video.overview?.let {
                Text(
                    it,
                    Modifier.widthIn(max = 840.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Bar(
    state: TvPlayerUiState.Playing,
    position: Duration,
    onAction: (TvPlayerAction) -> Unit,
    onTracks: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(EDGE), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titleOf(state.video), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Progress(position, state.duration)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MqTvButton(
                    stringResource(if (state.isPlaying) R.string.tv_player_pause else R.string.tv_player_play),
                    { onAction(TvPlayerAction.TogglePlayPause) },
                    Modifier.focusRequester(first),
                    icon = if (state.isPlaying) MqTvIcons.Pause else MqTvIcons.Play,
                )
                MqTvButton(
                    stringResource(R.string.tv_player_back_10),
                    { onAction(TvPlayerAction.Seek(forward = false)) },
                )
                MqTvButton(
                    stringResource(R.string.tv_player_forward_30),
                    { onAction(TvPlayerAction.Seek(forward = true)) },
                )
                if (state.tracks?.streams?.isNotEmpty() == true) {
                    MqTvButton(stringResource(R.string.tv_movie_tracks), onTracks)
                }
                if (state.hasNext) {
                    MqTvButton(
                        stringResource(R.string.tv_player_next),
                        { onAction(TvPlayerAction.PlayNextNow) },
                        icon = MqTvIcons.SkipNext,
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** The bar's line: elapsed, the bar, the total and when it ends ("ends at 22:41"). */
@Composable
private fun Progress(
    position: Duration,
    duration: Duration,
) {
    val fraction = if (duration > Duration.ZERO) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
    val left = (duration - position).coerceAtLeast(Duration.ZERO)
    val endsAt =
        LocalTime
            .now()
            .plusSeconds(left.inWholeSeconds)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val track = Color.White.copy(alpha = 0.3f)
    val bar = MaterialTheme.colorScheme.primary
    Column(Modifier.widthIn(max = 1600.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(6.dp).drawBehind {
                drawRect(track)
                drawRect(bar, size = Size(size.width * fraction, size.height))
            },
        )
        Row {
            Text(formatDuration(position), style = MaterialTheme.typography.labelLarge, color = Color.White)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.tv_player_ends_at, formatDuration(duration), endsAt),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun NextCard(
    seconds: Int,
    onAction: (TvPlayerAction) -> Unit,
    modifier: Modifier,
) {
    val now = remember { FocusRequester() }
    Surface(modifier.padding(EDGE), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tv_player_next_in, seconds), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MqTvButton(stringResource(R.string.tv_player_play_now), {
                    onAction(TvPlayerAction.PlayNextNow)
                }, Modifier.focusRequester(now))
                MqTvButton(stringResource(R.string.tv_series_confirm_cancel), { onAction(TvPlayerAction.CancelNext) })
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { now.requestFocus() } }
}

@Composable
private fun PlayerTracksMenu(
    tracks: VideoTracks,
    onAction: (TvPlayerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val trackFormat = stringResource(R.string.tv_movie_track)
    val current = stringResource(R.string.tv_movie_track_current)
    val audioLabel = stringResource(R.string.tv_movie_audio)
    val subtitleLabel = stringResource(R.string.tv_movie_subtitles)
    val off = stringResource(R.string.tv_movie_subtitles_off)

    fun MediaStreamInfo.label() = displayTitle ?: title ?: language ?: trackFormat.format(index)

    fun mark(
        label: String,
        on: Boolean,
    ) = if (on) "$label · $current" else label
    val audio = tracks.streams.filter { it.type == StreamType.Audio }
    val playingAudio =
        tracks.resolved.audioIndex ?: audio.firstOrNull { it.isDefault }?.index ?: audio.firstOrNull()?.index
    val entries =
        audio.map { stream ->
            MqTvMenuEntry(mark("$audioLabel: ${stream.label()}", stream.index == playingAudio), null) {
                onAction(TvPlayerAction.ChooseAudio(stream.index))
            }
        } +
            MqTvMenuEntry(mark("$subtitleLabel: $off", tracks.resolved.subtitleIndex == null), null) {
                onAction(TvPlayerAction.ChooseSubtitle(null))
            } +
            tracks.streams.filter { it.type == StreamType.Subtitle }.map { stream ->
                val label = mark("$subtitleLabel: ${stream.label()}", stream.index == tracks.resolved.subtitleIndex)
                MqTvMenuEntry(label, null) { onAction(TvPlayerAction.ChooseSubtitle(stream.index)) }
            }
    MqTvMenu(stringResource(R.string.tv_movie_tracks), entries, onDismiss)
}

/** A movie's title, or "Series · S2 · E5 · Episode title". */
private fun titleOf(video: Video): String =
    if (video.kind == VideoKind.Episode) {
        listOfNotNull(
            video.seriesName,
            episodeCode(video).takeIf { it != video.title },
            video.title,
        ).joinToString(" · ")
    } else {
        video.title
    }

private val EDGE = 48.dp
private val BAR_TIME = 5.seconds
private val SEEK_TIME = 2.seconds
