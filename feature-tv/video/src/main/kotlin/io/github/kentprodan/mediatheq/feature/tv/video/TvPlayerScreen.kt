package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
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
import io.github.kentprodan.mediatheq.core.data.DisplayMatch
import io.github.kentprodan.mediatheq.core.data.VideoTracks
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvCard
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMessage
import io.github.kentprodan.mediatheq.core.model.Chapter
import io.github.kentprodan.mediatheq.core.model.MediaSegment
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.PlayerExtras
import io.github.kentprodan.mediatheq.core.model.SegmentType
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.model.VideoQuality
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvSubtitles
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvTrickplayPreview
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvVideoSurface
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
    val style by viewModel.subtitleStyle.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    val skip by viewModel.skipButton.collectAsStateWithLifecycle()
    val displayMatch by viewModel.displayMatch.collectAsStateWithLifecycle()
    val format by viewModel.videoFormat.collectAsStateWithLifecycle()
    MatchDisplay(format, displayMatch)
    val stillWatching by viewModel.stillWatching.collectAsStateWithLifecycle()
    if (stillWatching) {
        MqTvMenu(
            stringResource(R.string.tv_player_still_watching),
            listOf(
                MqTvMenuEntry(stringResource(R.string.tv_player_keep_watching), null) {
                    viewModel.onAction(TvPlayerAction.KeepWatching)
                },
                MqTvMenuEntry(stringResource(R.string.tv_player_stop_watching), null) {
                    viewModel.onAction(TvPlayerAction.Leave)
                    onBack()
                },
            ),
        ) { viewModel.onAction(TvPlayerAction.KeepWatching) }
    }
    LaunchedEffect(state) { if (state == TvPlayerUiState.Ended) onBack() }
    TvPlayerScreen(
        state,
        position,
        nextIn,
        viewModel::onAction,
        quality = quality,
        extras = extras,
        skip = skip,
        onLeave = {
            viewModel.onAction(TvPlayerAction.Leave)
            onBack()
        },
    ) { scale ->
        MqTvVideoSurface(player, Modifier.fillMaxSize(), scale)
        MqTvSubtitles(player, Modifier.fillMaxSize(), style)
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
    quality: VideoQuality = VideoQuality.Original,
    extras: PlayerExtras = PlayerExtras(),
    /** The segment whose Skip button shows (#430); `null` for none. */
    skip: MediaSegment? = null,
    surface: @Composable (ContentScale) -> Unit,
) {
    // Fit each time a video opens; Fill and Stretch from the bar (owner, #429).
    var zoom by remember { mutableStateOf(TvZoom.Fit) }
    var menu by remember { mutableStateOf<PlayerMenu?>(null) }
    var bar by remember { mutableStateOf(false) }
    // Open over the bar: the bar waits for it (its keys never reach this screen's handler).
    var tracks by remember { mutableStateOf(false) }
    var keys by remember { mutableIntStateOf(0) }
    var seeking by remember { mutableIntStateOf(0) }
    val root = remember { FocusRequester() }
    BackHandler {
        if (bar) bar = false else onLeave()
    }
    LaunchedEffect(bar, keys, tracks, menu) {
        if (bar && !tracks && menu == null) {
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
    // Paused (#435): after a few minutes the movie's own screensaver; the screen may sleep only after a long pause.
    val paused = (state as? TvPlayerUiState.Playing)?.let { !it.isPlaying && !it.isBuffering } == true
    var saver by remember { mutableStateOf(false) }
    var sleepAllowed by remember { mutableStateOf(false) }
    LaunchedEffect(paused, keys) {
        saver = false
        sleepAllowed = false
        if (paused) {
            delay(SAVER_AFTER)
            saver = true
            delay(SLEEP_AFTER)
            sleepAllowed = true
        }
    }
    KeepScreenOn((state as? TvPlayerUiState.Playing)?.isPlaying == true || (paused && !sleepAllowed))
    val playing = state as? TvPlayerUiState.Playing
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(root)
            .onPreviewKeyEvent { event ->
                keys++
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    onAction(TvPlayerAction.KeyPressed)
                }
                // A key on the screensaver only wakes the player (the count above closes it).
                if (saver) return@onPreviewKeyEvent true
                // The bar's buttons and the next-episode card take their own keys.
                if (bar || nextIn != null) return@onPreviewKeyEvent false
                // Select on the Skip button while it shows (left and right still seek).
                if (skip != null && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    if (event.type == KeyEventType.KeyDown) onAction(TvPlayerAction.SkipSegment)
                    return@onPreviewKeyEvent true
                }
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
        surface(zoom.scale)
        when (state) {
            is TvPlayerUiState.Error -> {
                Surface(Modifier.fillMaxSize()) {
                    MqTvMessage(stringResource(R.string.tv_player_error), stringResource(R.string.tv_player_error_body))
                }
            }

            is TvPlayerUiState.Playing -> {
                if (!state.isPlaying && !state.isBuffering && !bar) PauseInfo(state.video)
                // Any key (counted above) brings the paused player back.
                if (saver && !bar) PauseScreensaver(state.video)
                if (bar) {
                    Bar(state, position, onAction, extras.chapters, onMenu = { menu = it }) { tracks = true }
                } else if (seeking > 0) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(EDGE)) {
                        extras.trickplay?.let { MqTvTrickplayPreview(it, position, Modifier.padding(bottom = 12.dp)) }
                        Progress(position, state.duration, extras.chapters.map { it.start })
                    }
                }
                skip?.takeIf { !bar }?.let { segment -> SkipButton(segment, Modifier.align(Alignment.BottomEnd)) }
                nextIn?.let { NextCard(it, onAction, Modifier.align(Alignment.BottomEnd)) }
            }

            TvPlayerUiState.Loading, TvPlayerUiState.Ended -> {
                Unit
            }
        }
    }
    playing?.tracks?.takeIf { tracks }?.let { PlayerTracksMenu(it, onAction) { tracks = false } }
    val current = stringResource(R.string.tv_movie_track_current)
    when (menu) {
        PlayerMenu.Quality -> {
            MqTvMenu(
                stringResource(R.string.tv_player_quality),
                QUALITIES.map { q ->
                    val label = stringResource(q.label)
                    MqTvMenuEntry(if (q == quality) "$label · $current" else label, null) {
                        onAction(TvPlayerAction.SetQuality(q))
                    }
                },
            ) { menu = null }
        }

        PlayerMenu.Chapters -> {
            ChaptersDialog(
                extras.chapters,
                position,
                onChoose = { onAction(TvPlayerAction.SeekTo(it)) },
            ) { menu = null }
        }

        PlayerMenu.Zoom -> {
            MqTvMenu(
                stringResource(R.string.tv_player_zoom),
                TvZoom.entries.map { z ->
                    val label = stringResource(z.label)
                    MqTvMenuEntry(if (z == zoom) "$label · $current" else label, null) { zoom = z }
                },
            ) { menu = null }
        }

        null -> {
            Unit
        }
    }
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

/**
 * The pause screensaver (#435): the video's backdrop drifting slowly, its title and the clock, so a long pause never
 * shows a still frame.
 */
@Composable
private fun PauseScreensaver(video: Video) {
    val drift = rememberInfiniteTransition(label = "drift")
    val scale by drift.animateFloat(
        1f,
        DRIFT_SCALE,
        infiniteRepeatable(tween(DRIFT_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale",
    )
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(CLOCK_TICK)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        (video.backdrop ?: video.artwork)?.let { art ->
            coil3.compose.AsyncImage(
                art,
                contentDescription = null,
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(EDGE)) {
            Text(titleOf(video), style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }
        Text(
            now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
            Modifier.align(Alignment.TopEnd).padding(EDGE),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
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
    chapters: List<Chapter>,
    onMenu: (PlayerMenu) -> Unit,
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
            Progress(position, state.duration, chapters.map { it.start })
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
                if (chapters.isNotEmpty()) {
                    MqTvButton(stringResource(R.string.tv_player_chapters), { onMenu(PlayerMenu.Chapters) })
                }
                MqTvButton(stringResource(R.string.tv_player_quality), { onMenu(PlayerMenu.Quality) })
                MqTvButton(stringResource(R.string.tv_player_zoom), { onMenu(PlayerMenu.Zoom) })
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
    /** Chapter starts, marked on the bar (#430). */
    marks: List<Duration> = emptyList(),
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
                if (duration > Duration.ZERO) {
                    marks.forEach { start ->
                        val x = size.width * (start / duration).toFloat()
                        drawRect(
                            Color.Black.copy(alpha = 0.7f),
                            topLeft = Offset(x, 0f),
                            size = Size(MARK_WIDTH, size.height),
                        )
                    }
                }
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

internal enum class PlayerMenu { Chapters, Quality, Zoom }

/** "Skip intro" or "Skip credits" (#430): Select skips while it shows; it goes after a few seconds by itself. */
@Composable
private fun SkipButton(
    segment: MediaSegment,
    modifier: Modifier,
) {
    Surface(modifier.padding(EDGE), shape = MaterialTheme.shapes.extraLarge) {
        Text(
            stringResource(
                if (segment.type ==
                    SegmentType.Outro
                ) {
                    R.string.tv_player_skip_credits
                } else {
                    R.string.tv_player_skip_intro
                },
            ),
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** The chapters with their pictures (#430); the one playing is focused first. */
@Composable
private fun ChaptersDialog(
    chapters: List<Chapter>,
    position: Duration,
    onChoose: (Duration) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = chapters.indexOfLast { it.start <= position }.coerceAtLeast(0)
    val focus = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.tv_player_chapters), style = MaterialTheme.typography.titleLarge)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    state = rememberLazyListState(current),
                ) {
                    itemsIndexed(chapters, key = { _, c -> c.index }) { i, chapter ->
                        MqTvCard(
                            chapter.name ?: stringResource(R.string.tv_player_chapter, chapter.index + 1),
                            {
                                onChoose(chapter.start)
                                onDismiss()
                            },
                            Modifier.width(CHAPTER_WIDTH).then(
                                if (i ==
                                    current
                                ) {
                                    Modifier.focusRequester(focus)
                                } else {
                                    Modifier
                                },
                            ),
                            subtitle = formatDuration(chapter.start),
                            imageAspectRatio = 16f / 9f,
                        ) {
                            chapter.imageUrl?.let {
                                coil3.compose.AsyncImage(
                                    it,
                                    null,
                                    Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        runCatching { focus.requestFocus() }
    }
}

private val CHAPTER_WIDTH = 200.dp
private const val MARK_WIDTH = 3f

/** Fit shows the whole picture, Fill crops it to the screen, Stretch fills it out of shape. */
internal enum class TvZoom(
    val scale: ContentScale,
    @param:StringRes val label: Int,
) {
    Fit(ContentScale.Fit, R.string.tv_player_zoom_fit),
    Fill(ContentScale.Crop, R.string.tv_player_zoom_fill),
    Stretch(ContentScale.FillBounds, R.string.tv_player_zoom_stretch),
}

/** Auto is the original stream when the network allows; the others ask the server to convert. */
private val QUALITIES = listOf(VideoQuality.Original, VideoQuality.P1080, VideoQuality.P720)

@get:StringRes
private val VideoQuality.label: Int
    get() =
        when (this) {
            VideoQuality.Original -> R.string.tv_player_quality_auto
            VideoQuality.P1080 -> R.string.tv_player_quality_1080
            VideoQuality.P720 -> R.string.tv_player_quality_720
            VideoQuality.P480, VideoQuality.P360 -> R.string.tv_player_quality_low
        }

private val EDGE = 48.dp
private val SAVER_AFTER = 5.minutes
private val SLEEP_AFTER = 30.minutes
private val CLOCK_TICK = 15.seconds
private const val DRIFT_MS = 30_000
private const val DRIFT_SCALE = 1.1f
private val BAR_TIME = 5.seconds
private val SEEK_TIME = 2.seconds

/**
 * Switches the TV to the video's frame rate (and resolution) while the player shows (#384) and back to the TV's own
 * mode when it closes; the display's modes come from Android, the choice from [chooseDisplayMode].
 */
@Composable
private fun MatchDisplay(
    format: Pair<Float, Int>?,
    match: DisplayMatch,
) {
    val activity = LocalActivity.current ?: return
    LaunchedEffect(format, match) {
        val (rate, height) = format ?: return@LaunchedEffect
        val display = activity.display ?: return@LaunchedEffect
        val modes =
            display.supportedModes.map {
                TvDisplayMode(
                    it.modeId,
                    it.physicalWidth,
                    it.physicalHeight,
                    it.refreshRate,
                )
            }
        val current = display.mode.let { TvDisplayMode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
        val chosen = chooseDisplayMode(rate, height, modes, current, match) ?: return@LaunchedEffect
        activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = chosen.id }
    }
    DisposableEffect(activity) {
        onDispose { activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = 0 } }
    }
}
