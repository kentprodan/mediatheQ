package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.data.AutoActionsPreferences

/**
 * Settings -> Playback -> Automatic actions (#141): every change applies at once. The summaries say what Android
 * allows (ADR 0033).
 */
internal fun LazyListScope.autoActionsGroups(
    actions: AutoActionsPreferences,
    onAction: (SettingsAction) -> Unit,
    onRestore: () -> Unit,
) {
    fun set(changed: AutoActionsPreferences) = onAction(SettingsAction.SetAutoActions(changed))
    item(key = "auto_actions") {
        Group(R.string.settings_aa_events) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_aa_volume),
                    stringResource(R.string.settings_aa_volume_summary),
                    actions.volumePause,
                ) { set(actions.copy(volumePause = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_aa_headphones),
                    stringResource(R.string.settings_aa_headphones_summary),
                    actions.headphonePlay,
                ) { set(actions.copy(headphonePlay = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_aa_bluetooth),
                    stringResource(R.string.settings_aa_bluetooth_summary),
                    actions.bluetoothPlay,
                ) { set(actions.copy(bluetoothPlay = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_aa_task_removal),
                    stringResource(R.string.settings_aa_task_removal_summary),
                    actions.pauseOnTaskRemoval,
                ) { set(actions.copy(pauseOnTaskRemoval = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_aa_remote_stop),
                    stringResource(R.string.settings_aa_remote_stop_summary),
                    actions.ignoreRemoteStop,
                ) { set(actions.copy(ignoreRemoteStop = it)) }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}
