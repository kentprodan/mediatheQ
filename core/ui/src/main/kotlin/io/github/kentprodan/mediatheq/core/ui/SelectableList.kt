package io.github.kentprodan.mediatheq.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import io.github.kentprodan.mediatheq.core.designsystem.component.bottomBarsPadding
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/**
 * A media list that can select (#123): while [selection] is active the [SelectionToolbar] floats at the bottom and
 * Back ends selecting. [content] gets the bottom space its list must leave (more while the toolbar shows).
 */
@Composable
fun SelectableList(
    selection: ListSelection<*>,
    onCommand: (SelectionCommand) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    extraActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (bottomSpace: Dp) -> Unit,
) {
    BackHandler(enabled = selection.isActive, onBack = onClear)
    Box(modifier) {
        content(if (selection.isActive) SelectionToolbarSpace else MediatheqSpacing.l)
        if (selection.isActive) {
            SelectionToolbar(
                selection.size,
                onCommand,
                onSelectAll,
                onClear,
                Modifier.align(Alignment.BottomCenter).bottomBarsPadding(),
                extraActions,
            )
        }
    }
}

/** A row's selected state: `null` outside selection, else whether [key] is selected. */
fun <K> ListSelection<K>.stateOf(key: K): Boolean? = if (isActive) key in this else null

/** A long press that starts (or extends) selecting with a haptic: `onLongClick = longPress(id)`. */
@Composable
fun <K> rememberSelectLongPress(onToggle: (K) -> Unit): (K) -> () -> Unit {
    val haptics = LocalHapticFeedback.current
    return { key ->
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle(key)
        }
    }
}
