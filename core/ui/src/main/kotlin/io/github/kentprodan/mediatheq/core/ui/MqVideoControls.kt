package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.common.formatDuration
import io.github.kentprodan.mediatheq.core.designsystem.component.MqSeekBar
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How far the -10 s / +10 s buttons jump. */
val VideoSkip: Duration = 10.seconds

/**
 * The video player's controls over the picture (ADR 0049): back and the title line at the top, -10 s, play / pause
 * (a loading indicator while buffering) and +10 s in the middle, the seek bar with times at the bottom, on a scrim that
 * keeps them readable over any frame. Stateless; always light on dark, whatever the theme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MqVideoControls(
    title: String,
    subtitle: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    position: Duration,
    duration: Duration,
    isSeekable: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Duration) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the audio and subtitles sheet (#274); `null` hides the button. */
    onTracks: (() -> Unit)? = null,
) {
    val skipBack = stringResource(R.string.core_ui_video_back_10)
    val skipForward = stringResource(R.string.core_ui_video_forward_10)
    // Dark whatever the app theme: the controls sit on a picture under a black scrim, and the seek bar's times need
    // the dark scheme's light text to be read there.
    MediatheqTheme(systemDark = true) {
        VideoControlsContent(
            title,
            subtitle,
            isPlaying,
            isBuffering,
            position,
            duration,
            isSeekable,
            onPlayPause,
            onSeek,
            onBack,
            skipBack,
            skipForward,
            modifier,
            onTracks,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoControlsContent(
    title: String,
    subtitle: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    position: Duration,
    duration: Duration,
    isSeekable: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Duration) -> Unit,
    onBack: () -> Unit,
    skipBack: String,
    skipForward: String,
    modifier: Modifier,
    onTracks: (() -> Unit)?,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Scrim, Color.Transparent, Scrim))),
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(MediatheqSpacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(MqIcons.ArrowBack, stringResource(R.string.core_ui_video_close), tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                onTracks?.let {
                    IconButton(onClick = it) {
                        Icon(MqIcons.Subtitles, stringResource(R.string.core_ui_tracks_open), tint = Color.White)
                    }
                }
            }
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.xl, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { onSeek((position - VideoSkip).coerceAtLeast(Duration.ZERO)) },
                    enabled = isSeekable,
                    modifier = Modifier.semantics { contentDescription = skipBack },
                ) { Text("−10") }
                Box(Modifier.size(PlayButtonSize), contentAlignment = Alignment.Center) {
                    if (isBuffering) {
                        LoadingIndicator(color = Color.White)
                    } else {
                        FilledIconButton(
                            onClick = onPlayPause,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(PlayButtonSize),
                        ) {
                            Icon(
                                if (isPlaying) MqIcons.PauseFilled else MqIcons.PlayArrow,
                                stringResource(
                                    if (isPlaying) R.string.core_ui_video_pause else R.string.core_ui_video_play,
                                ),
                            )
                        }
                    }
                }
                FilledTonalButton(
                    onClick = {
                        onSeek(
                            if (duration >
                                Duration.ZERO
                            ) {
                                minOf(position + VideoSkip, duration)
                            } else {
                                position + VideoSkip
                            },
                        )
                    },
                    enabled = isSeekable,
                    modifier = Modifier.semantics { contentDescription = skipForward },
                ) { Text("+10") }
            }
            MqSeekBar(
                progress = if (duration > Duration.ZERO) (position / duration).toFloat().coerceIn(0f, 1f) else 0f,
                elapsedLabel = formatDuration(position),
                totalLabel = formatDuration(duration),
                onSeek = { fraction -> onSeek(duration * fraction.toDouble()) },
                enabled = isSeekable,
            )
        }
    }
}

private val Scrim = Color.Black.copy(alpha = 0.6f)
private val PlayButtonSize = IconButtonDefaults.largeContainerSize().height
