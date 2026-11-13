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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqLoadingIndicator
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
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
    val playing = state as? VideoPlayerUiState.Playing
    FullScreenVideo(wide = (playing?.aspectRatio ?: 0f) > 1f)
    val leave = {
        viewModel.onAction(VideoPlayerAction.Leave)
        onBack()
    }
    BackHandler(onBack = leave)
    // The video ended and the music came back (ADR 0047): back to the page that opened it.
    LaunchedEffect(state) { if (state == VideoPlayerUiState.Ended) onBack() }
    VideoPlayerScreen(state, position, viewModel::onAction, leave) { MqVideoSurface(player, Modifier.fillMaxSize()) }
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
    surface: @Composable () -> Unit,
) {
    val toggleLabel = stringResource(R.string.video_player_toggle_controls)
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
                    )
                }
            }
        }
    }
}
