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
import io.github.kentprodan.mediatheq.core.data.SearchPagePreferences
import io.github.kentprodan.mediatheq.core.data.SearchSort
import io.github.kentprodan.mediatheq.core.data.TrackAction
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** The dialogs of Settings -> Search page (#136). */
enum class SearchPageDialog { TrackAction, Sort, Restore }

/** Settings -> Interface -> Page settings -> Search page (#136): every change applies at once. */
internal fun LazyListScope.searchPageGroups(
    page: SearchPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (SearchPageDialog) -> Unit,
) {
    val set = { it: SearchPagePreferences -> onAction(SettingsAction.SetSearchPage(it)) }
    item(key = "sp_results") {
        Group(R.string.settings_sp_results) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_sp_single_line_chips),
                    stringResource(R.string.settings_sp_single_line_chips_summary),
                    page.singleLineChips,
                ) { set(page.copy(singleLineChips = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_sp_show_favorite),
                    stringResource(R.string.settings_sp_show_favorite_summary),
                    page.showFavorite,
                ) { set(page.copy(showFavorite = it)) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_track_action),
                    stringResource(trackActionLabel(page.trackAction)),
                ) { onOpenDialog(SearchPageDialog.TrackAction) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_sp_sort),
                    stringResource(page.sort.label),
                ) { onOpenDialog(SearchPageDialog.Sort) }
            }
        }
    }
    restoreDefaultsGroup { onOpenDialog(SearchPageDialog.Restore) }
}

@Composable
internal fun SearchPageDialogHost(
    dialog: SearchPageDialog,
    page: SearchPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    if (dialog == SearchPageDialog.Restore) {
        RestoreDefaultsDialog({ onAction(SettingsAction.RestoreSearchPageDefaults) }, onDismiss)
        return
    }
    MqDialog(
        title = stringResource(dialog.title),
        confirmLabel = null,
        onConfirm = {},
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
    ) { SearchPageChoices(dialog, page, onAction, onDismiss) }
}

/** A dialog's content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
internal fun SearchPageDialogContent(
    dialog: SearchPageDialog,
    page: SearchPagePreferences,
) {
    MqDialogContent(stringResource(dialog.title), null, {}, stringResource(R.string.settings_cancel), {}) {
        SearchPageChoices(dialog, page, {}, {})
    }
}

/** One row per option, a check on the selected one; a tap applies it and closes the dialog. */
@Composable
private fun SearchPageChoices(
    dialog: SearchPageDialog,
    page: SearchPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows: List<Pair<String, SearchPagePreferences>> =
        when (dialog) {
            SearchPageDialog.TrackAction -> {
                (listOf(null) + TrackAction.entries).map {
                    stringResource(trackActionLabel(it)) to page.copy(trackAction = it)
                }
            }

            SearchPageDialog.Sort -> {
                SearchSort.entries.map { stringResource(it.label) to page.copy(sort = it) }
            }

            SearchPageDialog.Restore -> {
                emptyList()
            }
        }
    Column {
        rows.forEach { (label, choice) ->
            val selected = choice == page
            ListItem(
                headlineContent = { Text(label) },
                trailingContent = if (selected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier =
                    Modifier.selectable(selected, role = Role.RadioButton) {
                        onAction(SettingsAction.SetSearchPage(choice))
                        onDismiss()
                    },
            )
        }
    }
}

/** `null` = Same as default (the Navigation and media start setting). */
private fun trackActionLabel(action: TrackAction?): Int = action?.label ?: R.string.settings_sp_same_as_default

private val SearchPageDialog.title: Int
    get() =
        when (this) {
            SearchPageDialog.TrackAction -> R.string.settings_track_action
            SearchPageDialog.Sort -> R.string.settings_sp_sort
            SearchPageDialog.Restore -> R.string.settings_restore
        }

private val SearchSort.label: Int
    get() =
        when (this) {
            SearchSort.Relevance -> R.string.settings_sp_sort_relevance
            SearchSort.Title -> R.string.settings_ap_sort_title
        }
