package io.github.kentprodan.mediatheq.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val playing =
    VideoPlayerUiState.Playing(
        title = "Moșoi: Tribul Mic",
        subtitle = "2019",
        isPlaying = true,
        isBuffering = false,
        duration = 98.minutes,
        isSeekable = true,
        aspectRatio = 16 / 9f,
        controlsShown = true,
    )

/** The surface stand-in: layoutlib renders no SurfaceView. */
private val darkFrame: @Composable () -> Unit = { Box(Modifier.fillMaxSize().background(Color(0xFF1B2430))) }

@Composable
private fun Player(state: VideoPlayerUiState) =
    MediatheqTheme { VideoPlayerScreen(state, position = 41.minutes, onAction = {}, onBack = {}, surface = darkFrame) }

/** The player held sideways, controls shown while it plays. */
@PreviewTest
@Preview(name = "Landscape", device = "spec:width=915dp,height=412dp,dpi=420")
@Composable
fun VideoPlayerControlsScreenshot() = Player(playing)

/** Paused: the play button. */
@PreviewTest
@Preview(name = "Landscape", device = "spec:width=915dp,height=412dp,dpi=420")
@Composable
fun VideoPlayerPausedScreenshot() = Player(playing.copy(isPlaying = false))

/** Controls hidden: only the picture. */
@PreviewTest
@Preview(name = "Landscape", device = "spec:width=915dp,height=412dp,dpi=420")
@Composable
fun VideoPlayerControlsHiddenScreenshot() = Player(playing.copy(controlsShown = false))

/** Buffering: a loading indicator in place of play / pause. */
@PreviewTest
@Preview(name = "Landscape", device = "spec:width=915dp,height=412dp,dpi=420")
@Composable
fun VideoPlayerBufferingScreenshot() = Player(playing.copy(isBuffering = true))

/** The stream is being asked for. */
@PreviewTest
@MqPreviews
@Composable
fun VideoPlayerLoadingScreenshot() = Player(VideoPlayerUiState.Loading)

/** The video could not play. */
@PreviewTest
@MqPreviews
@Composable
fun VideoPlayerErrorScreenshot() = Player(VideoPlayerUiState.Error(MqError.Network))

/** A movie's page before #273: Play, and Resume at its resume point. */
@PreviewTest
@MqPreviews
@Composable
fun MoviePlaceholderScreenshot() =
    MediatheqTheme {
        MoviePlaceholder(
            Video(
                MediaId(ServerId("s"), "m1"),
                VideoKind.Movie,
                "Moșoi: Tribul Mic",
                year = 2019,
                userData = UserData(playbackPosition = 41.minutes),
            ),
            onPlay = { _: Duration -> },
            onBack = {},
        )
    }
