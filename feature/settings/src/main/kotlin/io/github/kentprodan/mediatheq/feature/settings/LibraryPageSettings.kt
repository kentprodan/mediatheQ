package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.kentprodan.mediatheq.core.data.LibraryHubSpacing
import io.github.kentprodan.mediatheq.core.data.LibraryHubStyle
import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.ui.label

/** The choice dialogs of Settings -> Library page (the Organize dialog is ViewModel state). */
enum class LibraryPageDialog { Style, Spacing, Restore }

/** Settings -> Interface -> Page settings -> Library page (#129): every change applies at once. */
internal fun LazyListScope.libraryPageGroups(
    page: LibraryPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (LibraryPageDialog) -> Unit,
) {
    val set = { changed: LibraryPagePreferences -> onAction(SettingsAction.SetLibraryPage(changed)) }
    item(key = "lp_tiles") {
        Group(R.string.settings_lp_group) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_lp_shortcuts),
                    page.shortcuts.map { stringResource(it.label) }.joinToString(),
                ) { onAction(SettingsAction.OpenLibraryShortcuts) }
            }
            item { shapes ->
                DialogItem(shapes, stringResource(R.string.settings_lp_style), stringResource(page.style.label)) {
                    onOpenDialog(LibraryPageDialog.Style)
                }
            }
            // Columns only mean something for the grid.
            if (page.style == LibraryHubStyle.Grid) {
                item { shapes ->
                    ChoiceItem(
                        shapes,
                        stringResource(R.string.settings_lp_columns),
                        null,
                        LibraryPagePreferences.COLUMNS.toList(),
                        selected = page.columns,
                        label = { it.toString() },
                        onSelect = { set(page.copy(columns = it)) },
                    )
                }
                item { shapes ->
                    ChoiceItem(
                        shapes,
                        stringResource(R.string.settings_lp_landscape_columns),
                        null,
                        LibraryPagePreferences.LANDSCAPE_COLUMNS.toList(),
                        selected = page.landscapeColumns,
                        label = { it.toString() },
                        onSelect = { set(page.copy(landscapeColumns = it)) },
                    )
                }
            }
            item { shapes ->
                DialogItem(shapes, stringResource(R.string.settings_lp_spacing), stringResource(page.spacing.label)) {
                    onOpenDialog(LibraryPageDialog.Spacing)
                }
            }
        }
    }
    restoreDefaultsGroup { onOpenDialog(LibraryPageDialog.Restore) }
}

@Composable
internal fun LibraryPageDialogHost(
    dialog: LibraryPageDialog,
    page: LibraryPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    if (dialog == LibraryPageDialog.Restore) {
        RestoreDefaultsDialog({ onAction(SettingsAction.RestoreLibraryPageDefaults) }, onDismiss)
        return
    }
    MqDialog(
        title = stringResource(dialog.title),
        confirmLabel = null,
        onConfirm = {},
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
    ) { LibraryPageChoices(dialog, page, onAction, onDismiss) }
}

/** A choice dialog's content on its own, for screenshot tests. */
@Composable
internal fun LibraryPageDialogContent(
    dialog: LibraryPageDialog,
    page: LibraryPagePreferences,
) {
    MqDialogContent(stringResource(dialog.title), null, {}, stringResource(R.string.settings_cancel), {}) {
        LibraryPageChoices(dialog, page, {}, {})
    }
}

/** The Library page's "Organize" dialog: check tiles (at least one) and drag them into order; OK applies. */
@Composable
internal fun LibraryShortcutsDialog(
    draft: OrganizeDraft<LibraryShortcut>,
    onAction: (SettingsAction) -> Unit,
) {
    OrganizeDialog(
        stringResource(R.string.settings_lp_shortcuts),
        draft,
        label = { stringResource(it.label) },
        onToggle = { onAction(SettingsAction.ToggleLibraryShortcut(it)) },
        onMove = { from, to -> onAction(SettingsAction.MoveLibraryShortcut(from, to)) },
        onConfirm = { onAction(SettingsAction.ConfirmLibraryShortcuts) },
        onDismiss = { onAction(SettingsAction.DismissLibraryShortcuts) },
    )
}

/** One row per option, a check on the selected one; a tap applies it and closes the dialog. */
@Composable
private fun LibraryPageChoices(
    dialog: LibraryPageDialog,
    page: LibraryPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows: List<Pair<Int, LibraryPagePreferences>> =
        when (dialog) {
            LibraryPageDialog.Style -> LibraryHubStyle.entries.map { it.label to page.copy(style = it) }
            LibraryPageDialog.Spacing -> LibraryHubSpacing.entries.map { it.label to page.copy(spacing = it) }
            LibraryPageDialog.Restore -> emptyList()
        }
    Column {
        rows.forEach { (label, choice) ->
            val selected = choice == page
            ListItem(
                headlineContent = { Text(stringResource(label)) },
                trailingContent = if (selected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier =
                    Modifier.selectable(selected, role = Role.RadioButton) {
                        onAction(SettingsAction.SetLibraryPage(choice))
                        onDismiss()
                    },
            )
        }
    }
}

private val LibraryPageDialog.title: Int
    get() =
        when (this) {
            LibraryPageDialog.Style -> R.string.settings_lp_style
            LibraryPageDialog.Spacing -> R.string.settings_lp_spacing
            LibraryPageDialog.Restore -> R.string.settings_restore
        }

private val LibraryHubStyle.label: Int
    get() =
        when (this) {
            LibraryHubStyle.Grid -> R.string.settings_lp_style_grid
            LibraryHubStyle.List -> R.string.settings_lp_style_list
        }

private val LibraryHubSpacing.label: Int
    get() =
        when (this) {
            LibraryHubSpacing.Compact -> R.string.settings_lp_spacing_compact
            LibraryHubSpacing.Normal -> R.string.settings_lp_spacing_normal
            LibraryHubSpacing.Large -> R.string.settings_lp_spacing_large
        }
