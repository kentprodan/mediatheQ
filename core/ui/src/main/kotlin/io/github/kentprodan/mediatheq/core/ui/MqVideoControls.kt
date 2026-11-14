package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    /** Lines under the subtitle (#276): what plays, "Converted by the server", Dolby Vision not available. */
    notes: List<String> = emptyList(),
    /** Tapping the title (#276): shows or hides the info line; `null` = not tappable. */
    onTitleClick: (() -> Unit)? = null,
    /** Chapter starts (#277): marks on the seek bar. */
    chapters: List<Duration> = emptyList(),
    /** Opens the chapters sheet (#277); `null` hides the button. */
    onChapters: (() -> Unit)? = null,
    /** The position being dragged to, `null` once let go (#277). */
    onScrub: (Duration?) -> Unit = {},
    /** Shown above the seek bar while dragging (#277): the trickplay preview of [Duration]. */
    preview: @Composable (Duration) -> Unit = {},
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
            notes,
            onTitleClick,
            chapters,
            onChapters,
            onScrub,
            preview,
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
    notes: List<String>,
    onTitleClick: (() -> Unit)?,
    chapters: List<Duration>,
    onChapters: (() -> Unit)?,
    onScrub: (Duration?) -> Unit,
    preview: @Composable (Duration) -> Unit,
) {
    var scrubbing by remember { mutableStateOf<Duration?>(null) }
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
                Column(Modifier.weight(1f).then(onTitleClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)) {
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
                    notes.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                onChapters?.let {
                    IconButton(onClick = it) {
                        Icon(MqIcons.Checklist, stringResource(R.string.core_ui_chapters_open), tint = Color.White)
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
            scrubbing?.let { at ->
                // No height of its own: the preview floats above the seek bar, centred on the thumb, and the
                // buttons above stay where they are (review).
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(0.dp).wrapContentHeight(Alignment.Bottom, unbounded = true),
                ) {
                    val fraction = if (duration > Duration.ZERO) (at / duration).toFloat().coerceIn(0f, 1f) else 0f
                    var previewWidth by remember { mutableIntStateOf(0) }
                    val density = LocalDensity.current
                    val half = with(density) { (previewWidth / 2).toDp() }
                    val x = (maxWidth * fraction - half).coerceIn(0.dp, (maxWidth - half * 2).coerceAtLeast(0.dp))
                    Box(Modifier.offset(x = x).onSizeChanged { previewWidth = it.width }) { preview(at) }
                }
            }
            MqSeekBar(
                progress = if (duration > Duration.ZERO) (position / duration).toFloat().coerceIn(0f, 1f) else 0f,
                elapsedLabel = formatDuration(position),
                totalLabel = formatDuration(duration),
                onSeek = { fraction -> onSeek(duration * fraction.toDouble()) },
                enabled = isSeekable,
                marks =
                    if (duration > Duration.ZERO) {
                        chapters.filter { it > Duration.ZERO }.map { (it / duration).toFloat() }
                    } else {
                        emptyList()
                    },
                onScrub = { fraction ->
                    scrubbing = fraction?.let { duration * it.toDouble() }
                    onScrub(scrubbing)
                },
            )
        }
    }
}

private val Scrim = Color.Black.copy(alpha = 0.6f)
private val PlayButtonSize = IconButtonDefaults.largeContainerSize().height
