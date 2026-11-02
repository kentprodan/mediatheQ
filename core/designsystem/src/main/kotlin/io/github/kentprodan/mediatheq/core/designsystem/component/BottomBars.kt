package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The height of what the app shell draws over the bottom of the screens: the mini player and the navigation bar or
 * floating toolbar (#122). Screens scroll under them (the bar can be see-through), so scrolling content ends with
 * [plusBottomBars] and fixed content uses [bottomBarsPadding]. 0 outside the shell (previews, tests).
 */
val LocalBottomBarsPadding = compositionLocalOf { 0.dp }

/** [this] plus the bottom bars at the bottom: the content padding of a screen's scrolling list. */
@Composable
fun PaddingValues.plusBottomBars(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + LocalBottomBarsPadding.current,
    )
}

/** Keeps fixed content (empty, loading and error states) above the bottom bars. */
@Composable
fun Modifier.bottomBarsPadding(): Modifier = padding(bottom = LocalBottomBarsPadding.current)
