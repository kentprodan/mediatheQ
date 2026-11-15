package io.github.kentprodan.mediatheq.feature.video

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqLoadingIndicator
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.PlayerExtras
import io.github.kentprodan.mediatheq.core.model.SegmentType
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.ui.MqSubtitles
import io.github.kentprodan.mediatheq.core.ui.MqTracksSheet
import io.github.kentprodan.mediatheq.core.ui.MqVideoControls
import io.github.kentprodan.mediatheq.core.ui.MqVideoSurface
import kotlin.time.Duration

/**
 * The video player destination (ADR 0049): the session's picture, the controls, landscape while a wide video plays,
 * no system bars and the screen kept on while playing; all of it undone on leave. Back pauses (ADR 0047).
 */
@Composable
internal fun VideoPlayerRoute(
    key: VideoPlayerKey,
    onBack: () -> Unit,
    viewModel: VideoPlayerViewModel =
        hiltViewModel<VideoPlayerViewModel, VideoPlayerViewModel.Factory>(
            key = "${key.id}:${key.attach}",
            creationCallback = { it.create(key) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val nextIn by viewModel.nextIn.collectAsStateWithLifecycle()
    val skip by viewModel.skipButton.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    val skipped by viewModel.skippedNotice.collectAsStateWithLifecycle()
    val playing = state as? VideoPlayerUiState.Playing
    val inPip = rememberIsInPipMode()
    val pip by viewModel.pip.collectAsStateWithLifecycle()
    PipParams(pip, sourceRect = null)
    // In PiP the window is the activity's: no orientation lock or hidden bars to keep (#278).
    if (!inPip) FullScreenVideo(wide = (playing?.aspectRatio ?: 0f) > 1f)
    if (inPip) {
        // Only the picture and its subtitles in the small window (#278).
        Box(Modifier.fillMaxSize()) {
            MqVideoSurface(player, Modifier.fillMaxSize())
            MqSubtitles(player, Modifier.fillMaxSize())
        }
        return
    }
    val leave = {
        viewModel.onAction(VideoPlayerAction.Leave)
        onBack()
    }
    BackHandler(onBack = leave)
    // The video ended and the music came back (ADR 0047): back to the page that opened it.
    LaunchedEffect(state) { if (state == VideoPlayerUiState.Ended) onBack() }
    VideoPlayerScreen(
        state,
        position,
        viewModel::onAction,
        leave,
        nextIn = nextIn,
        skip = skip?.type,
        skipped = skipped,
        extras = extras,
    ) {
        Box(Modifier.fillMaxSize()) {
            MqVideoSurface(player, Modifier.fillMaxSize())
            MqSubtitles(player, Modifier.fillMaxSize())
        }
    }
}

/** Landscape for a [wide] video and no system bars; the window as it was on leave. */
@Composable
private fun FullScreenVideo(wide: Boolean) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    // Saved: turning the phone recreates the activity, which then already is in landscape (not what to go back to).
    val original = rememberSaveable { activity.requestedOrientation }
    DisposableEffect(wide) {
        if (wide) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = original }
    }
    DisposableEffect(Unit) {
        val bars = WindowCompat.getInsetsController(activity.window, view)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { bars.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/**
 * Stateless player: [surface] (the picture; a dark box in previews, which cannot render a `SurfaceView`) under the
 * controls, which a tap on the picture shows or hides.
 */
@Composable
fun VideoPlayerScreen(
    state: VideoPlayerUiState,
    position: Duration,
    onAction: (VideoPlayerAction) -> Unit,
    onBack: () -> Unit,
    /** Seconds until the next episode, during the countdown (#275); `null` hides the card. */
    nextIn: Int? = null,
    /** The segment kind whose skip button shows (#277), `null` for none. */
    skip: SegmentType? = null,
    /** A segment kind just skipped by itself (#277): a short note. */
    skipped: SegmentType? = null,
    /** Chapters and seek previews (#277). */
    extras: PlayerExtras = PlayerExtras(),
    surface: @Composable () -> Unit,
) {
    val toggleLabel = stringResource(R.string.video_player_toggle_controls)
    // The info line under the title (#276), toggled by tapping it.
    var infoShown by rememberSaveable { mutableStateOf(false) }
    var chaptersOpen by rememberSaveable { mutableStateOf(false) }
    var tracksOpen by rememberSaveable { mutableStateOf(false) }
    val keepOn = (state as? VideoPlayerUiState.Playing)?.isPlaying == true
    Box(
        Modifier
            .fillMaxSize()
            // Only while it plays, and without touching the window's own flag (Settings -> Keep screen on).
            .then(if (keepOn) Modifier.keepScreenOn() else Modifier)
            .background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                onAction(VideoPlayerAction.ToggleControls)
            }.semantics { onClick(toggleLabel) { onAction(VideoPlayerAction.ToggleControls).let { true } } },
        contentAlignment = Alignment.Center,
    ) {
        surface()
        when (state) {
            VideoPlayerUiState.Loading, VideoPlayerUiState.Ended -> {
                MqLoadingIndicator()
            }

            is VideoPlayerUiState.Error -> {
                // The empty state's colors are the theme's: on the black player it sits on the theme's surface.
                Surface(Modifier.fillMaxSize()) {
                    MqEmptyState(
                        MqIcons.Movie,
                        stringResource(R.string.video_player_error_title),
                        body =
                            stringResource(
                                if (state.error == MqError.NotFound) {
                                    R.string.video_player_not_found_body
                                } else {
                                    R.string.video_player_error_body
                                },
                            ),
                        actionLabel = stringResource(R.string.video_back),
                        onAction = onBack,
                    )
                }
            }

            is VideoPlayerUiState.Playing -> {
                AnimatedVisibility(state.controlsShown, enter = fadeIn(), exit = fadeOut()) {
                    MqVideoControls(
                        title = state.title,
                        subtitle = state.subtitle,
                        isPlaying = state.isPlaying,
                        isBuffering = state.isBuffering,
                        position = position,
                        duration = state.duration,
                        isSeekable = state.isSeekable,
                        onPlayPause = { onAction(VideoPlayerAction.TogglePlayPause) },
                        onSeek = { onAction(VideoPlayerAction.SeekTo(it)) },
                        onBack = onBack,
                        onTracks = state.tracks?.let { { tracksOpen = true } },
                        notes = videoNotes(state.info, infoShown, state.dolbyVisionUnavailable),
                        onTitleClick = { infoShown = !infoShown },
                        chapters = extras.chapters.map { it.start },
                        onChapters = extras.chapters.takeIf { it.isNotEmpty() }?.let { { chaptersOpen = true } },
                        preview = { at -> extras.trickplay?.let { TrickplayPreview(it, at) } },
                    )
                }
                // The countdown takes the corner when both would show.
                if (nextIn == null) {
                    skip?.let { type ->
                        MediatheqTheme(systemDark = true) {
                            Button(
                                onClick = { onAction(VideoPlayerAction.SkipSegment) },
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .windowInsetsPadding(WindowInsets.safeDrawing)
                                        .padding(bottom = SKIP_ABOVE_SEEK_BAR, end = MediatheqSpacing.xl),
                            ) { Text(stringResource(type.skipLabel)) }
                        }
                    }
                }
                skipped?.let { type ->
                    MediatheqTheme(systemDark = true) {
                        Surface(
                            Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(MediatheqSpacing.xl)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                stringResource(type.skippedLabel),
                                Modifier.padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                nextIn?.let { seconds ->
                    NextEpisodeCard(
                        seconds,
                        onCancel = { onAction(VideoPlayerAction.CancelNext) },
                        onPlayNow = { onAction(VideoPlayerAction.PlayNextNow) },
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(MediatheqSpacing.xl),
                    )
                }
                if (chaptersOpen) {
                    ChaptersSheet(
                        extras.chapters,
                        onChapter = { onAction(VideoPlayerAction.SeekTo(it.start)) },
                        onDismiss = { chaptersOpen = false },
                    )
                }
                val tracks = state.tracks
                if (tracksOpen && tracks != null) {
                    MqTracksSheet(
                        tracks.sheet(stringResource(R.string.video_tracks_off), stringResource(R.string.video_track)),
                        onAudio = { onAction(VideoPlayerAction.ChooseAudio(it)) },
                        onSubtitle = { onAction(VideoPlayerAction.ChooseSubtitle(it)) },
                        onWholeSeason = { onAction(VideoPlayerAction.WholeSeason(it)) },
                        onDismiss = { tracksOpen = false },
                    )
                }
            }
        }
    }
}

/** "Next episode in 8 s" with Cancel and Play now, over the picture's corner (#275); dark whatever the theme. */
@Composable
internal fun NextEpisodeCard(
    seconds: Int,
    onCancel: () -> Unit,
    onPlayNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediatheqTheme(systemDark = true) {
        Card(
            modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                Modifier.padding(MediatheqSpacing.l),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            ) {
                Text(
                    pluralStringResource(R.plurals.video_next_episode_in, seconds, seconds),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.video_next_cancel)) }
                    Button(onClick = onPlayNow) { Text(stringResource(R.string.video_next_play_now)) }
                }
            }
        }
    }
}

/** The skip button sits above the seek bar and its times. */
private val SKIP_ABOVE_SEEK_BAR = 120.dp

private val SegmentType.skipLabel: Int
    get() =
        when (this) {
            SegmentType.Intro -> R.string.video_skip_intro
            SegmentType.Outro -> R.string.video_skip_credits
            SegmentType.Recap -> R.string.video_skip_recap
            SegmentType.Preview -> R.string.video_skip_preview
            SegmentType.Commercial -> R.string.video_skip_commercial
        }

private val SegmentType.skippedLabel: Int
    get() =
        when (this) {
            SegmentType.Intro -> R.string.video_skipped_intro
            SegmentType.Outro -> R.string.video_skipped_credits
            SegmentType.Recap -> R.string.video_skipped_recap
            SegmentType.Preview -> R.string.video_skipped_preview
            SegmentType.Commercial -> R.string.video_skipped_commercial
        }
