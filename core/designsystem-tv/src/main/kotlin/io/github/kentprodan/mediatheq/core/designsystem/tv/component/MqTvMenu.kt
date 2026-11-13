package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** One choice of an [MqTvMenu]. */
data class MqTvMenuEntry(
    val label: String,
    val icon: ImageVector?,
    val onClick: () -> Unit,
)

/**
 * An item's menu (#236, #238): a dialog with [title] and [entries], focus on the first. Choosing one runs it and closes
 * the menu. A long-press opens it while select is still held: that key's repeats and release are ignored, select
 * counts only after a fresh press in the menu.
 */
@Composable
fun MqTvMenu(
    title: String,
    entries: List<MqTvMenuEntry>,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier
                    .width(360.dp)
                    .padding(16.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.key !in SELECT_KEYS) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) armed = true
                        !armed
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                entries.forEachIndexed { index, entry ->
                    MqTvListItem(
                        entry.label,
                        {
                            entry.onClick()
                            onDismiss()
                        },
                        if (index == 0) Modifier.focusRequester(first) else Modifier,
                        icon = entry.icon,
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private val SELECT_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
