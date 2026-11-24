package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.delay

/**
 * The guideline's featured carousel (#521): auto-advance every [advanceMillis] while it does not have the focus, Left
 * and Right change the slide (Left on the first slide leaves, to the menu), pagination dots at the bottom end. [slide]
 * draws one item, its picture included.
 *
 * Our own instead of tv-material's `Carousel` (#600): that one stops advancing whenever any accessibility service is on
 * (launchers and button remappers too, not only TalkBack) and then pulls the focus back to itself on every slide.
 */
@Composable
fun MqTvCarousel(
    itemCount: Int,
    modifier: Modifier = Modifier,
    advanceMillis: Long = ADVANCE_MILLIS,
    slide: @Composable BoxScope.(index: Int) -> Unit,
) {
    var active by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    var focused by remember { mutableStateOf(false) }
    val count by rememberUpdatedState(itemCount)
    val current = if (itemCount > 0) active.coerceIn(0, itemCount - 1) else 0
    LaunchedEffect(focused, current, itemCount) {
        if (!focused && itemCount > 1) {
            delay(advanceMillis)
            forward = true
            active = (current + 1) % itemCount
        }
    }
    Box(
        modifier
            .onFocusChanged { focused = it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // The state, not `current`: two quick presses arrive before the next composition.
                val now = active.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                val next =
                    when (event.key) {
                        Key.DirectionLeft -> now - 1
                        Key.DirectionRight -> now + 1
                        else -> return@onPreviewKeyEvent false
                    }
                if (next !in 0 until itemCount) return@onPreviewKeyEvent false
                forward = next > now
                active = next
                true
            }.focusGroup(),
    ) {
        if (itemCount > 0) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    val sign = if (forward) 1 else -1
                    (slideInHorizontally { sign * it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -sign * it / 4 } + fadeOut())
                },
                label = "MqTvCarousel",
            ) { index ->
                val requester = remember { FocusRequester() }
                // Live, unlike `current`: a slide's content is kept from when it first came, also while it leaves.
                val target = transition.targetState == EnterExitState.Visible
                // The slide that had the focus leaves with its button: hand the focus to the new one. Keyed on
                // `target`, so a slide that comes back before it finished leaving (Right then Left) asks again.
                LaunchedEffect(target) { if (target && focused) requester.requestFocus() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(requester)
                        // A leaving slide takes no focus (Up during an automatic change), it would lose it.
                        .focusProperties { onEnter = { if (!target) cancelFocusChange() } }
                        .focusGroup(),
                ) {
                    // The list may have shrunk under a leaving slide.
                    if (index < count) slide(index)
                }
            }
        }
        if (itemCount > 1) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(DOTS_PADDING),
                horizontalArrangement = Arrangement.spacedBy(DOT),
            ) {
                val colors = MaterialTheme.colorScheme
                repeat(itemCount) { i ->
                    Box(
                        Modifier
                            .size(DOT)
                            .background(
                                if (i == current) colors.onSurface else colors.onSurface.copy(alpha = 0.4f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

/** About every 8 s (#521, owner decision). */
const val ADVANCE_MILLIS = 8_000L

private val DOT = 8.dp
private val DOTS_PADDING = 24.dp
