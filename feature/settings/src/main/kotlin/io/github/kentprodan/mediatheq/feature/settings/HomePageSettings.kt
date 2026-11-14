package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.data.HomePreferences
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqIconToggle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqReorderState
import io.github.kentprodan.mediatheq.core.designsystem.component.mqReorderHandle
import io.github.kentprodan.mediatheq.core.designsystem.component.mqReorderItem
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.ui.title

/** The confirmation dialogs of Settings -> Page settings -> Home page. */
enum class HomePageDialog { ResetRows, Restore }

/**
 * Settings -> Interface -> Page settings -> Home page (#127): every section in Home's order, first in the list (so a
 * row's list index is its section index for the reorder state), each with a drag handle, its title and an eye; then
 * Reset active rows and Restore defaults. [rows] is the order shown, which a drag moves before it is dropped and saved.
 */
internal fun LazyListScope.homePageGroups(
    home: HomePreferences,
    rows: List<HomeSection>,
    reorder: MqReorderState,
    onAction: (SettingsAction) -> Unit,
    onOpenSection: (HomeSection) -> Unit,
    onOpenDialog: (HomePageDialog) -> Unit,
) {
    itemsIndexed(rows, key = { _, section -> section }) { index, section ->
        HomeSectionRow(
            section = section,
            shown = section !in home.hidden,
            index = index,
            count = rows.size,
            onMove = { to ->
                // The other mode's sections keep their place after the rows shown (#275).
                val moved = rows.toMutableList().apply { add(to, removeAt(index)) }
                onAction(SettingsAction.SetHomeOrder(moved + home.order.filterNot { it in rows }))
            },
            onToggle = { shown ->
                onAction(SettingsAction.SetHomeHidden(if (shown) home.hidden - section else home.hidden + section))
            },
            onOpenSettings = { onOpenSection(section) },
            modifier = Modifier.mqReorderItem(reorder, section),
            handle = Modifier.mqReorderHandle(reorder, section),
        )
    }
    item(key = "home_reset") {
        Group(R.string.settings_home_rows_group) {
            item { shapes ->
                SegmentedListItem(
                    onClick = { onOpenDialog(HomePageDialog.ResetRows) },
                    shapes = shapes,
                    colors = settingsColors(),
                    supportingContent = { Text(stringResource(R.string.settings_home_reset_rows_summary)) },
                    leadingContent = { Icon(MqIcons.Restore, contentDescription = null) },
                ) { Text(stringResource(R.string.settings_home_reset_rows)) }
            }
        }
    }
    restoreDefaultsGroup { onOpenDialog(HomePageDialog.Restore) }
}

/** One section: drag handle, title, eye (shown / hidden; a hidden row is dimmed); Move up / Move down for TalkBack. */
@Composable
private fun HomeSectionRow(
    section: HomeSection,
    shown: Boolean,
    index: Int,
    count: Int,
    onMove: (to: Int) -> Unit,
    onToggle: (shown: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
    handle: Modifier,
) {
    val moveUp = stringResource(R.string.settings_move_up)
    val moveDown = stringResource(R.string.settings_move_down)
    Surface(
        modifier.fillMaxWidth().semantics {
            customActions =
                listOfNotNull(
                    CustomAccessibilityAction(moveUp) {
                        onMove(index - 1)
                        true
                    }.takeIf { index > 0 },
                    CustomAccessibilityAction(moveDown) {
                        onMove(index + 1)
                        true
                    }.takeIf { index < count - 1 },
                )
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.heightIn(min = MediatheqSpacing.xxl).padding(horizontal = MediatheqSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
        ) {
            // A drag handle, not a button: the custom actions move the row for TalkBack.
            Icon(MqIcons.DragHandle, contentDescription = null, handle)
            Text(
                stringResource(section.title),
                Modifier.weight(1f).alpha(if (shown) 1f else HIDDEN_ALPHA),
                style = MaterialTheme.typography.bodyLarge,
            )
            // The gear (#182): the section's own settings.
            IconButton(onClick = onOpenSettings) {
                Icon(
                    MqIcons.Settings,
                    contentDescription =
                        stringResource(
                            R.string.settings_home_section_settings,
                            stringResource(section.title),
                        ),
                )
            }
            MqIconToggle(
                checked = shown,
                onClick = { onToggle(!shown) },
                icon = if (shown) MqIcons.Visibility else MqIcons.VisibilityOff,
                contentDescription = stringResource(R.string.settings_home_show, stringResource(section.title)),
                stateDescription =
                    stringResource(
                        if (shown) R.string.settings_home_shown else R.string.settings_home_hidden,
                    ),
            )
        }
    }
}

@Composable
internal fun HomePageDialogHost(
    dialog: HomePageDialog,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        HomePageDialog.Restore -> {
            RestoreDefaultsDialog({ onAction(SettingsAction.RestoreHomeDefaults) }, onDismiss)
        }

        HomePageDialog.ResetRows -> {
            MqDialog(
                title = null,
                confirmLabel = stringResource(R.string.settings_home_reset_rows),
                onConfirm = {
                    onAction(SettingsAction.ResetHomeRows)
                    onDismiss()
                },
                dismissLabel = stringResource(R.string.settings_cancel),
                onDismiss = onDismiss,
            ) { Text(stringResource(R.string.settings_home_reset_rows_message)) }
        }
    }
}

// Dimmed, still readable (the eye says hidden too, so the dimming is not the only signal).
private const val HIDDEN_ALPHA = 0.6f
