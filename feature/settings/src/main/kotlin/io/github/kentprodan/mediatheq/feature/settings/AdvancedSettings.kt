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
import io.github.kentprodan.mediatheq.core.data.AdvancedPreferences
import io.github.kentprodan.mediatheq.core.data.CornerStyle
import io.github.kentprodan.mediatheq.core.data.KeepScreenOn
import io.github.kentprodan.mediatheq.core.data.ScreenOrientation
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** The dialogs of Settings -> Advanced settings (#137). */
enum class AdvancedDialog { KeepScreenOn, Orientation, Corners, Restore }

/** Settings -> Interface -> Advanced settings (#137): every change applies at once, app-wide. */
internal fun LazyListScope.advancedGroups(
    advanced: AdvancedPreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (AdvancedDialog) -> Unit,
) {
    val set = { it: AdvancedPreferences -> onAction(SettingsAction.SetAdvanced(it)) }
    item(key = "adv_display") {
        Group(R.string.settings_adv_display) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_adv_keep_screen_on),
                    stringResource(advanced.keepScreenOn.label),
                ) { onOpenDialog(AdvancedDialog.KeepScreenOn) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_adv_orientation),
                    stringResource(advanced.orientation.label),
                ) { onOpenDialog(AdvancedDialog.Orientation) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_adv_corners),
                    stringResource(advanced.corners.label),
                ) { onOpenDialog(AdvancedDialog.Corners) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_adv_track_number),
                    stringResource(R.string.settings_adv_track_number_summary),
                    advanced.showTrackNumber,
                ) { set(advanced.copy(showTrackNumber = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_adv_hide_status_bar),
                    stringResource(R.string.settings_adv_hide_status_bar_summary),
                    advanced.hideStatusBar,
                ) { set(advanced.copy(hideStatusBar = it)) }
            }
        }
    }
    restoreDefaultsGroup { onOpenDialog(AdvancedDialog.Restore) }
}

@Composable
internal fun AdvancedDialogHost(
    dialog: AdvancedDialog,
    advanced: AdvancedPreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    if (dialog == AdvancedDialog.Restore) {
        RestoreDefaultsDialog({ onAction(SettingsAction.RestoreAdvancedDefaults) }, onDismiss)
        return
    }
    MqDialog(
        title = stringResource(dialog.title),
        confirmLabel = null,
        onConfirm = {},
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
    ) { AdvancedChoices(dialog, advanced, onAction, onDismiss) }
}

/** A dialog's content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
internal fun AdvancedDialogContent(
    dialog: AdvancedDialog,
    advanced: AdvancedPreferences,
) {
    MqDialogContent(stringResource(dialog.title), null, {}, stringResource(R.string.settings_cancel), {}) {
        AdvancedChoices(dialog, advanced, {}, {})
    }
}

/** One row per option, a check on the selected one; a tap applies it and closes the dialog. */
@Composable
private fun AdvancedChoices(
    dialog: AdvancedDialog,
    advanced: AdvancedPreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows: List<Pair<String, AdvancedPreferences>> =
        when (dialog) {
            AdvancedDialog.KeepScreenOn -> {
                KeepScreenOn.entries.map { stringResource(it.label) to advanced.copy(keepScreenOn = it) }
            }

            AdvancedDialog.Orientation -> {
                ScreenOrientation.entries.map { stringResource(it.label) to advanced.copy(orientation = it) }
            }

            AdvancedDialog.Corners -> {
                CornerStyle.entries.map { stringResource(it.label) to advanced.copy(corners = it) }
            }

            AdvancedDialog.Restore -> {
                emptyList()
            }
        }
    Column {
        rows.forEach { (label, choice) ->
            val selected = choice == advanced
            ListItem(
                headlineContent = { Text(label) },
                trailingContent = if (selected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier =
                    Modifier.selectable(selected, role = Role.RadioButton) {
                        onAction(SettingsAction.SetAdvanced(choice))
                        onDismiss()
                    },
            )
        }
    }
}

private val AdvancedDialog.title: Int
    get() =
        when (this) {
            AdvancedDialog.KeepScreenOn -> R.string.settings_adv_keep_screen_on
            AdvancedDialog.Orientation -> R.string.settings_adv_orientation
            AdvancedDialog.Corners -> R.string.settings_adv_corners
            AdvancedDialog.Restore -> R.string.settings_restore
        }

private val KeepScreenOn.label: Int
    get() =
        when (this) {
            KeepScreenOn.Off -> R.string.settings_adv_off
            KeepScreenOn.NowPlaying -> R.string.settings_adv_keep_now_playing
            KeepScreenOn.Always -> R.string.settings_adv_always
        }

private val ScreenOrientation.label: Int
    get() =
        when (this) {
            ScreenOrientation.System -> R.string.settings_adv_orientation_system
            ScreenOrientation.Portrait -> R.string.settings_adv_orientation_portrait
            ScreenOrientation.Landscape -> R.string.settings_adv_orientation_landscape
        }

private val CornerStyle.label: Int
    get() =
        when (this) {
            CornerStyle.Small -> R.string.settings_adv_corners_small
            CornerStyle.Normal -> R.string.settings_adv_corners_normal
            CornerStyle.Large -> R.string.settings_adv_corners_large
        }
