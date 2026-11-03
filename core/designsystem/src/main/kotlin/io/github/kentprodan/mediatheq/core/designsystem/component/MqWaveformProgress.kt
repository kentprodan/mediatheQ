package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.designsystem.R
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes

/** How a waveform is drawn (#124); the choice comes with the Now playing settings (#126). */
enum class MqWaveformStyle { Sharp, Smooth, Bars, BarsReflected }

/** What shows while a track has no waveform yet (a converted stream, a file still loading). */
enum class MqProgressFallback { Basic, Wavy }

/**
 * A seekable playback position (#124): the track's waveform, played part `primary`, the rest `secondaryContainer`
 * (the inactive track of the Slider, so every look matches). Without [levels] it shows the [fallback] and crossfades to
 * the waveform when they arrive, at the same position; the waveform grows in. Tap seeks at once; a drag shows its
 * value until release, then seeks once ([onSeek] gets 0..1). TalkBack reads a slider: [positionText] ("1:23 of 4:05").
 */
@Composable
fun MqWaveformProgress(
    levels: List<Float>?,
    progress: Float,
    positionText: String,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: MqWaveformStyle = MqWaveformStyle.Sharp,
    fallback: MqProgressFallback = MqProgressFallback.Basic,
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = dragged ?: progress.coerceIn(0f, 1f)
    val description = stringResource(R.string.mq_seek)
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = levels,
        modifier = modifier.fillMaxWidth().height(MediatheqSizes.waveformHeight),
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
        contentAlignment = Alignment.Center,
        label = "waveform",
        // Only the arrival (or loss) of data crossfades; another track's levels redraw in place.
        contentKey = { it != null },
    ) { data ->
        if (data == null && fallback == MqProgressFallback.Basic) {
            // Today's seek bar, dragged exactly as before.
            Slider(
                value = shown,
                onValueChange = { dragged = it },
                onValueChangeFinished = {
                    dragged?.let(onSeek)
                    dragged = null
                },
                enabled = enabled,
                modifier =
                    Modifier.fillMaxSize().semantics {
                        contentDescription = description
                        stateDescription = positionText
                    },
            )
        } else {
            SeekArea(
                shown = shown,
                enabled = enabled,
                description = description,
                positionText = positionText,
                onDrag = { dragged = it },
                onSeek = {
                    dragged = null
                    onSeek(it)
                },
            ) {
                if (data == null) {
                    // Read-only on its own: the seek area around it carries the gestures and the one slider node.
                    LinearWavyProgressIndicator(
                        progress = { shown },
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center).clearAndSetSemantics {},
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                } else {
                    Waveform(data, shown, style, enabled)
                }
            }
        }
    }
}

/** Tap and horizontal drag over the whole area, with haptics, and the slider semantics. */
@Composable
private fun SeekArea(
    shown: Float,
    enabled: Boolean,
    description: String,
    positionText: String,
    onDrag: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val drag by rememberUpdatedState(onDrag)
    val seek by rememberUpdatedState(onSeek)
    var last by remember { mutableStateOf(shown) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    seek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }.pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        last = (offset.x / size.width).coerceIn(0f, 1f)
                        drag(last)
                    },
                    onDragEnd = {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        seek(last)
                    },
                    // Interrupted (another gesture took over): back to the playing position, no seek.
                    onDragCancel = { drag(null) },
                ) { change, _ ->
                    last = (change.position.x / size.width).coerceIn(0f, 1f)
                    drag(last)
                }
            }.semantics {
                contentDescription = description
                stateDescription = positionText
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                if (enabled) {
                    setProgress { value ->
                        seek(value.coerceIn(0f, 1f))
                        true
                    }
                } else {
                    disabled()
                }
            },
        content = content,
    )
}

@Composable
private fun Waveform(
    levels: List<Float>,
    progress: Float,
    style: MqWaveformStyle,
    enabled: Boolean,
) {
    // Grows in when the data arrives; previews and screenshots show it whole.
    val inspection = LocalInspectionMode.current
    val grow = remember { Animatable(if (inspection) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) { grow.animateTo(1f, spec) }
    val played = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val rest = MaterialTheme.colorScheme.secondaryContainer
    Canvas(Modifier.fillMaxSize()) {
        val split = size.width * progress
        clipRect(right = split) { drawWaveform(levels, style, grow.value, played) }
        clipRect(left = split) { drawWaveform(levels, style, grow.value, rest) }
    }
}

private fun DrawScope.drawWaveform(
    levels: List<Float>,
    style: MqWaveformStyle,
    grow: Float,
    color: Color,
) {
    val minimum = MIN_LINE.toPx()
    when (style) {
        MqWaveformStyle.Sharp, MqWaveformStyle.Smooth -> {
            val center = size.height / 2
            val step = size.width / (levels.size - 1).coerceAtLeast(1)
            val half = levels.map { maxOf(it * grow * size.height / 2, minimum / 2) }
            val path =
                Path().apply {
                    moveTo(0f, center - half.first())
                    edge(half, step, style == MqWaveformStyle.Smooth) { center - it }
                    lineTo(size.width, center + half.last())
                    edge(half.asReversed(), -step, style == MqWaveformStyle.Smooth, start = size.width) { center + it }
                    close()
                }
            drawPath(path, color)
        }

        MqWaveformStyle.Bars, MqWaveformStyle.BarsReflected -> {
            val barWidth = BAR_WIDTH.toPx()
            val count = (size.width / (barWidth + BAR_GAP.toPx())).toInt().coerceAtLeast(1)
            val pitch = size.width / count
            // The reflection takes the lower third, faded; plain bars are mirrored around the middle.
            val baseline = if (style == MqWaveformStyle.Bars) size.height / 2 else size.height * 2 / 3
            val radius = CornerRadius(barWidth / 2)
            repeat(count) { bar ->
                val from = bar * levels.size / count
                val to = maxOf(from + 1, (bar + 1) * levels.size / count)
                val level = levels.subList(from, to).max() * grow
                val x = bar * pitch + (pitch - barWidth) / 2
                val up = maxOf(level * baseline, minimum)
                drawRoundRect(color, Offset(x, baseline - up), Size(barWidth, up), radius)
                val down = maxOf(level * (size.height - baseline), minimum)
                if (style == MqWaveformStyle.Bars) {
                    drawRoundRect(color, Offset(x, baseline), Size(barWidth, down), radius)
                } else {
                    drawRoundRect(
                        color,
                        Offset(x, baseline + REFLECTION_GAP.toPx()),
                        Size(barWidth, down),
                        radius,
                        alpha = REFLECTION_ALPHA,
                    )
                }
            }
        }
    }
}

/** Adds one edge through [half] heights mapped by [y], left to right ([step] > 0) or back. */
private inline fun Path.edge(
    half: List<Float>,
    step: Float,
    smooth: Boolean,
    start: Float = 0f,
    y: (Float) -> Float,
) {
    half.forEachIndexed { i, h ->
        val x = start + i * step
        if (smooth && i > 0) {
            val px = start + (i - 1) * step
            quadraticTo(px, y(half[i - 1]), (px + x) / 2, (y(half[i - 1]) + y(h)) / 2)
        } else {
            lineTo(x, y(h))
        }
    }
}

private val MIN_LINE = 2.dp
private val BAR_WIDTH = 3.dp
private val BAR_GAP = 2.dp
private val REFLECTION_GAP = 1.dp
private const val REFLECTION_ALPHA = 0.35f
