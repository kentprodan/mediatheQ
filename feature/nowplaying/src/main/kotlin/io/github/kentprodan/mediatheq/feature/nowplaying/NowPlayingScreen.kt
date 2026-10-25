package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFavoriteToggle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqPlayerCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqSeekBar
import io.github.kentprodan.mediatheq.core.designsystem.component.MqStarRating
import io.github.kentprodan.mediatheq.core.designsystem.component.MqTransportControls
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.ui.formatDuration
import io.github.kentprodan.mediatheq.core.ui.formatFrequency
import kotlin.time.Duration

/**
 * The full player: large cover, title and artist, seek bar and transport, on a gradient from the (cover) theme's
 * primary container to its surface. Swiping down or the close button returns to the mini player.
 * [position] is read only by the seek bar, so position ticks recompose nothing else.
 */
@Composable
fun NowPlayingScreen(
    state: NowPlayingUiState.Playing,
    position: () -> Duration,
    onAction: (NowPlayingAction) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenQueue: () -> Unit = {},
    onOpenAudio: () -> Unit = {},
    cover: Any? = state.artwork,
    coverModifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // No Surface under the gradient, so set the text and icon color the surface would give.
    val paneTitle = stringResource(R.string.nowplaying_pane_title)
    CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
        NowPlayingContent(
            state,
            position,
            onAction,
            onCollapse,
            onOpenQueue,
            onOpenAudio,
            modifier.semantics { this.paneTitle = paneTitle },
            cover,
            coverModifier,
        )
    }
}

@Composable
private fun NowPlayingContent(
    state: NowPlayingUiState.Playing,
    position: () -> Duration,
    onAction: (NowPlayingAction) -> Unit,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenAudio: () -> Unit,
    modifier: Modifier,
    cover: Any?,
    coverModifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface)))
            .swipeDownToCollapse(onCollapse)
            .safeDrawingPadding()
            .padding(horizontal = MediatheqSpacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.l),
    ) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = onCollapse) {
                Icon(MqIcons.KeyboardArrowDown, contentDescription = stringResource(R.string.nowplaying_collapse))
            }
        }
        // Takes what is left, so the controls always fit (e.g. at fontScale 2.0).
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            MqPlayerCover(model = cover, modifier = coverModifier, blurHash = state.artwork?.blurHash)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                state.title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                state.artist,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MqStarRating(stars = state.stars, onRate = { onAction(NowPlayingAction.Rate(it)) })
            state.bitPerfectSampleRateHz?.let { BitPerfectBadge(it) }
        }
        SeekSection(state, position, onAction)
        MqTransportControls(
            isPlaying = state.isPlaying,
            hasPrevious = state.hasPrevious,
            hasNext = state.hasNext,
            onPrevious = { onAction(NowPlayingAction.Previous) },
            onPlayPause = { onAction(NowPlayingAction.TogglePlayPause) },
            onNext = { onAction(NowPlayingAction.Next) },
        )
        // The secondary row (expressive-design section 5); lyrics and output come in their issues.
        Row(
            Modifier.fillMaxWidth().padding(bottom = MediatheqSpacing.l),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MqFavoriteToggle(state.isFavorite, onToggle = { onAction(NowPlayingAction.SetFavorite(it)) })
            IconButton(onClick = onOpenAudio) {
                Icon(MqIcons.Tune, contentDescription = stringResource(R.string.nowplaying_audio_open))
            }
            IconButton(onClick = onOpenQueue) {
                Icon(MqIcons.QueueMusic, contentDescription = stringResource(R.string.nowplaying_queue_open))
            }
        }
    }
}

/** Bit-perfect USB is on for this track: its sample rate, and that the phone volume does nothing. */
@Composable
private fun BitPerfectBadge(sampleRateHz: Int) {
    Column(
        Modifier.padding(top = MediatheqSpacing.s).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                stringResource(R.string.nowplaying_bit_perfect_badge, formatFrequency(sampleRateHz)),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = MediatheqSpacing.m, vertical = MediatheqSpacing.xs),
            )
        }
        Text(
            stringResource(R.string.nowplaying_bit_perfect_volume),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SeekSection(
    state: NowPlayingUiState.Playing,
    position: () -> Duration,
    onAction: (NowPlayingAction) -> Unit,
) {
    val elapsed = position().coerceIn(Duration.ZERO, state.duration)
    val progress = if (state.duration.isPositive()) (elapsed / state.duration).toFloat() else 0f
    MqSeekBar(
        progress = progress,
        elapsedLabel = formatDuration(elapsed),
        totalLabel = formatDuration(state.duration),
        onSeek = { onAction(NowPlayingAction.SeekTo(it)) },
        enabled = state.isSeekable,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Closes on a downward fling, or a drag over a quarter of the screen. */
@Composable
internal fun Modifier.swipeDownToCollapse(onCollapse: () -> Unit): Modifier {
    var height by remember { mutableIntStateOf(0) }
    var dragged by remember { mutableFloatStateOf(0f) }
    val flingVelocity = flingVelocityPx()
    return onSizeChanged { height = it.height }
        .draggable(
            state = rememberDraggableState { dragged += it },
            orientation = Orientation.Vertical,
            onDragStarted = { dragged = 0f },
            onDragStopped = { velocity ->
                if (velocity > flingVelocity || dragged > height / 4f) onCollapse()
            },
        )
}

/** The speed that counts as a fling, in pixels per second (about 600 dp/s on any screen density). */
@Composable
internal fun flingVelocityPx(): Float = FLING_DP_PER_SECOND * LocalDensity.current.density

private const val FLING_DP_PER_SECOND = 600f
