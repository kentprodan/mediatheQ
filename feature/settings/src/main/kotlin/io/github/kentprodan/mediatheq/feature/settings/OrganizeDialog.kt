package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.component.mqReorderHandle
import io.github.kentprodan.mediatheq.core.designsystem.component.mqReorderItem
import io.github.kentprodan.mediatheq.core.designsystem.component.rememberMqReorderState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes

/**
 * An "Organize" dialog (navigation entries #122, Library tiles #129): check items and drag them into order (Move up /
 * Move down for TalkBack); OK applies, Cancel drops the draft. Items must be saveable (enums): they key the list.
 */
@Composable
internal fun <T : Any> OrganizeDialog(
    title: String,
    draft: OrganizeDraft<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    summary: String? = null,
) {
    MqDialog(
        title = title,
        confirmLabel = stringResource(R.string.settings_ok),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
    ) { OrganizeList(draft, label, onToggle, onMove, summary) }
}

/** The dialog's content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
internal fun <T : Any> OrganizeDialogContent(
    title: String,
    draft: OrganizeDraft<T>,
    label: @Composable (T) -> String,
    summary: String? = null,
) {
    MqDialogContent(
        title,
        stringResource(R.string.settings_ok),
        {},
        stringResource(R.string.settings_cancel),
        {},
    ) { OrganizeList(draft, label, {}, { _, _ -> }, summary) }
}

@Composable
internal fun <T : Any> OrganizeList(
    draft: OrganizeDraft<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    summary: String? = null,
) {
    // A local copy follows the finger while dragging; the draft gets one move when it is dropped.
    var rows by remember(draft.order) { mutableStateOf(draft.order) }
    val listState = rememberLazyListState()
    val reorder =
        rememberMqReorderState(
            listState,
            onMove = { from, to -> rows = rows.toMutableList().apply { add(to, removeAt(from)) } },
            onDrop = { key, to ->
                val from = draft.order.indexOf(key)
                if (from != to) onMove(from, to)
            },
        )
    Column {
        summary?.let { Text(it) }
        LazyColumn(Modifier.heightIn(max = MediatheqSizes.dialogListMaxHeight), listState) {
            items(rows, key = { it }) { item ->
                OrganizeRow(
                    label(item),
                    checked = item in draft.checked,
                    enabled = draft.canToggle(item),
                    index = rows.indexOf(item),
                    count = rows.size,
                    onToggle = { onToggle(item) },
                    onMove = onMove,
                    modifier = Modifier.mqReorderItem(reorder, item),
                    handle = Modifier.mqReorderHandle(reorder, item),
                )
            }
        }
    }
}

@Composable
private fun OrganizeRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    index: Int,
    count: Int,
    onToggle: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier,
    handle: Modifier,
) {
    val moveUp = stringResource(R.string.settings_move_up)
    val moveDown = stringResource(R.string.settings_move_down)
    Row(
        modifier.semantics {
            customActions =
                buildList {
                    if (index > 0) {
                        add(
                            CustomAccessibilityAction(moveUp) {
                                onMove(index, index - 1)
                                true
                            },
                        )
                    }
                    if (index < count - 1) {
                        add(
                            CustomAccessibilityAction(moveDown) {
                                onMove(index, index + 1)
                                true
                            },
                        )
                    }
                }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListItem(
            headlineContent = { Text(label) },
            leadingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier =
                Modifier
                    .weight(1f)
                    .toggleable(checked, enabled = enabled, role = Role.Checkbox) { onToggle() },
        )
        // A drag handle, not a button: the row's custom actions move it for TalkBack.
        Icon(MqIcons.DragHandle, contentDescription = null, handle)
    }
}
