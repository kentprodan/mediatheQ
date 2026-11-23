package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Carousel
import androidx.tv.material3.CarouselDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.rememberCarouselState

/**
 * The guideline's featured carousel (#521): tv-material's `Carousel` (auto-advance every [advanceMillis], paused while
 * it has the focus, Left and Right change the slide) with pagination dots at the bottom end. [slide] draws one item,
 * its picture included; the opt-in stays here.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MqTvCarousel(
    itemCount: Int,
    modifier: Modifier = Modifier,
    advanceMillis: Long = ADVANCE_MILLIS,
    slide: @Composable BoxScope.(index: Int) -> Unit,
) {
    val state = rememberCarouselState()
    Carousel(
        itemCount = itemCount,
        modifier = modifier,
        carouselState = state,
        autoScrollDurationMillis = advanceMillis,
        carouselIndicator = {
            if (itemCount > 1) {
                CarouselDefaults.IndicatorRow(
                    itemCount = itemCount,
                    activeItemIndex = state.activeItemIndex,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(DOTS_PADDING),
                ) { active ->
                    val colors = MaterialTheme.colorScheme
                    Box(
                        Modifier
                            .size(DOT)
                            .background(
                                if (active) colors.onSurface else colors.onSurface.copy(alpha = 0.4f),
                                CircleShape,
                            ),
                    )
                }
            }
        },
    ) { index ->
        Box(
            Modifier
                .focusProperties {
                    // tv-material lets focus leave left from a later slide's button (#569: the menu opened from
                    // slide 3). Cancelled, the Carousel reads the move as Left: the previous slide.
                    onExit = { if (index > 0 && requestedFocusDirection == FocusDirection.Left) cancelFocusChange() }
                }.focusGroup(),
        ) { slide(index) }
    }
}

/** About every 8 s (#521, owner decision). */
const val ADVANCE_MILLIS = 8_000L

private val DOT = 8.dp
private val DOTS_PADDING = 24.dp
