package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.kentprodan.mediatheq.core.data.PlaylistButton
import io.github.kentprodan.mediatheq.core.data.PlaylistHeaderBackground
import io.github.kentprodan.mediatheq.core.data.PlaylistPagePreferences
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** The dialogs of Settings -> Playlist page (#135). */
enum class PlaylistPageDialog { Background, HiddenButtons, DynamicColors, Restore }

/** Settings -> Interface -> Page settings -> Playlist page (#135): every change applies at once. */
internal fun LazyListScope.playlistPageGroups(
    page: PlaylistPagePreferences,
    onOpenDialog: (PlaylistPageDialog) -> Unit,
) {
    item(key = "pp_header") {
        Group(R.string.settings_gp_header) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_gp_background),
                    stringResource(page.headerBackground.label),
                ) { onOpenDialog(PlaylistPageDialog.Background) }
            }
            item { shapes ->
                val hidden = PlaylistButton.entries.filter { it in page.hiddenButtons }.map { stringResource(it.label) }
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_gp_hidden_buttons),
                    hidden.joinToString(", ").ifEmpty { stringResource(R.string.settings_ml_none) },
                ) { onOpenDialog(PlaylistPageDialog.HiddenButtons) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_gp_dynamic_colors),
                    stringResource(playlistColorsLabel(page.dynamicColors)),
                ) { onOpenDialog(PlaylistPageDialog.DynamicColors) }
            }
        }
    }
    restoreDefaultsGroup { onOpenDialog(PlaylistPageDialog.Restore) }
}

@Composable
internal fun PlaylistPageDialogHost(
    dialog: PlaylistPageDialog,
    page: PlaylistPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        PlaylistPageDialog.Restore -> {
            RestoreDefaultsDialog({ onAction(SettingsAction.RestorePlaylistPageDefaults) }, onDismiss)
        }

        PlaylistPageDialog.HiddenButtons -> {
            // The checks live here until OK; Cancel drops them.
            var hidden by rememberSaveable { mutableStateOf(page.hiddenButtons.toList()) }
            MqDialog(
                title = stringResource(dialog.title),
                confirmLabel = stringResource(R.string.settings_ok),
                onConfirm = {
                    onAction(SettingsAction.SetPlaylistPage(page.copy(hiddenButtons = hidden.toSet())))
                    onDismiss()
                },
                dismissLabel = stringResource(R.string.settings_cancel),
                onDismiss = onDismiss,
            ) { HiddenButtons(hidden.toSet()) { hidden = it.toList() } }
        }

        else -> {
            MqDialog(
                title = stringResource(dialog.title),
                confirmLabel = null,
                onConfirm = {},
                dismissLabel = stringResource(R.string.settings_cancel),
                onDismiss = onDismiss,
            ) { PlaylistPageChoices(dialog, page, onAction, onDismiss) }
        }
    }
}

/** A dialog's content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
internal fun PlaylistPageDialogContent(
    dialog: PlaylistPageDialog,
    page: PlaylistPagePreferences,
) {
    val ok = stringResource(R.string.settings_ok).takeIf { dialog == PlaylistPageDialog.HiddenButtons }
    MqDialogContent(stringResource(dialog.title), ok, {}, stringResource(R.string.settings_cancel), {}) {
        if (dialog == PlaylistPageDialog.HiddenButtons) {
            HiddenButtons(page.hiddenButtons) {}
        } else {
            PlaylistPageChoices(dialog, page, {}, {})
        }
    }
}

@Composable
private fun HiddenButtons(
    hidden: Set<PlaylistButton>,
    onChange: (Set<PlaylistButton>) -> Unit,
) {
    Column {
        PlaylistButton.entries.forEach { button ->
            val checked = button in hidden
            ListItem(
                headlineContent = { Text(stringResource(button.label)) },
                leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier =
                    Modifier.toggleable(checked, role = Role.Checkbox) {
                        onChange(if (checked) hidden - button else hidden + button)
                    },
            )
        }
    }
}

/** One row per option, a check on the selected one; a tap applies it and closes the dialog. */
@Composable
private fun PlaylistPageChoices(
    dialog: PlaylistPageDialog,
    page: PlaylistPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows: List<Pair<String, PlaylistPagePreferences>> =
        when (dialog) {
            PlaylistPageDialog.Background -> {
                PlaylistHeaderBackground.entries.map { stringResource(it.label) to page.copy(headerBackground = it) }
            }

            PlaylistPageDialog.DynamicColors -> {
                listOf(false, true).map { stringResource(playlistColorsLabel(it)) to page.copy(dynamicColors = it) }
            }

            PlaylistPageDialog.HiddenButtons, PlaylistPageDialog.Restore -> {
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
                        onAction(SettingsAction.SetPlaylistPage(choice))
                        onDismiss()
                    },
            )
        }
    }
}

private fun playlistColorsLabel(on: Boolean): Int =
    if (on) R.string.settings_pp_dynamic_colors_image else R.string.settings_arp_dynamic_colors_off

private val PlaylistPageDialog.title: Int
    get() =
        when (this) {
            PlaylistPageDialog.Background -> R.string.settings_gp_background
            PlaylistPageDialog.HiddenButtons -> R.string.settings_gp_hidden_buttons
            PlaylistPageDialog.DynamicColors -> R.string.settings_gp_dynamic_colors
            PlaylistPageDialog.Restore -> R.string.settings_restore
        }

private val PlaylistHeaderBackground.label: Int
    get() =
        when (this) {
            PlaylistHeaderBackground.Image -> R.string.settings_gp_background_image
            PlaylistHeaderBackground.ImageColor -> R.string.settings_arp_background_color
            PlaylistHeaderBackground.None -> R.string.settings_ap_background_none
        }

private val PlaylistButton.label: Int
    get() =
        when (this) {
            PlaylistButton.Resume -> R.string.settings_ap_button_resume
            PlaylistButton.Shuffle -> R.string.settings_ap_button_shuffle
            PlaylistButton.PlayNext -> R.string.settings_ap_button_play_next
            PlaylistButton.AddToQueue -> R.string.settings_ap_button_add_to_queue
            PlaylistButton.Favorite -> R.string.settings_ap_button_favorite
        }
