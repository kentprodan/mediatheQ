package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.data.HeadsetAction
import io.github.kentprodan.mediatheq.core.data.HeadsetPreferences
import io.github.kentprodan.mediatheq.core.data.NextButtonAction
import io.github.kentprodan.mediatheq.core.data.PreviousButtonAction

/** The choice dialogs of Settings -> Playback -> Headset buttons (#139). */
enum class HeadsetDialog(
    val title: Int,
) {
    Single(R.string.settings_hb_single),
    Double(R.string.settings_hb_double),
    Triple(R.string.settings_hb_triple),
    Next(R.string.settings_hb_next),
    Previous(R.string.settings_hb_previous),
}

/** Settings -> Playback -> Headset buttons (#139): every change applies at once. */
internal fun LazyListScope.headsetGroups(
    headset: HeadsetPreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (HeadsetDialog) -> Unit,
    onRestore: () -> Unit,
) {
    item(key = "headset") {
        Group(R.string.settings_hb_buttons) {
            HeadsetDialog.entries.forEach { dialog ->
                item { shapes ->
                    DialogItem(shapes, stringResource(dialog.title), headset.valueOf(dialog)) { onOpenDialog(dialog) }
                }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_hb_slow),
                    stringResource(R.string.settings_hb_slow_summary),
                    headset.slowClicks,
                ) { onAction(SettingsAction.SetHeadset(headset.copy(slowClicks = it))) }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

@Composable
private fun HeadsetPreferences.valueOf(dialog: HeadsetDialog): String =
    when (dialog) {
        HeadsetDialog.Single -> stringResource(singleClick.label)
        HeadsetDialog.Double -> stringResource(doubleClick.label)
        HeadsetDialog.Triple -> stringResource(tripleClick.label)
        HeadsetDialog.Next -> stringResource(nextButton.label)
        HeadsetDialog.Previous -> stringResource(previousButton.label)
    }

@Composable
internal fun HeadsetDialogHost(
    dialog: HeadsetDialog,
    headset: HeadsetPreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(dialog.title)
    val set = { it: HeadsetPreferences -> onAction(SettingsAction.SetHeadset(it)) }
    when (dialog) {
        HeadsetDialog.Single -> {
            ClickDialog(title, headset.singleClick, { set(headset.copy(singleClick = it)) }, onDismiss)
        }

        HeadsetDialog.Double -> {
            ClickDialog(title, headset.doubleClick, { set(headset.copy(doubleClick = it)) }, onDismiss)
        }

        HeadsetDialog.Triple -> {
            ClickDialog(title, headset.tripleClick, { set(headset.copy(tripleClick = it)) }, onDismiss)
        }

        HeadsetDialog.Next -> {
            ChoiceDialog(title, NextButtonAction.entries, headset.nextButton, { stringResource(it.label) }, {
                set(headset.copy(nextButton = it))
            }, onDismiss)
        }

        HeadsetDialog.Previous -> {
            ChoiceDialog(title, PreviousButtonAction.entries, headset.previousButton, { stringResource(it.label) }, {
                set(headset.copy(previousButton = it))
            }, onDismiss)
        }
    }
}

@Composable
private fun ClickDialog(
    title: String,
    selected: HeadsetAction,
    onSelect: (HeadsetAction) -> Unit,
    onDismiss: () -> Unit,
) = ChoiceDialog(title, HeadsetAction.entries, selected, { stringResource(it.label) }, onSelect, onDismiss)

/** A headset dialog's content, for screenshot tests. */
@Composable
internal fun HeadsetDialogContent(
    dialog: HeadsetDialog,
    headset: HeadsetPreferences,
) {
    val title = stringResource(dialog.title)
    when (dialog) {
        HeadsetDialog.Next -> {
            ChoiceDialogContent(title, NextButtonAction.entries, headset.nextButton) { stringResource(it.label) }
        }

        HeadsetDialog.Previous -> {
            ChoiceDialogContent(title, PreviousButtonAction.entries, headset.previousButton) {
                stringResource(it.label)
            }
        }

        else -> {
            ChoiceDialogContent(title, HeadsetAction.entries, headset.singleClick) { stringResource(it.label) }
        }
    }
}

private val HeadsetAction.label: Int
    get() =
        when (this) {
            HeadsetAction.PlayPause -> R.string.settings_hb_play_pause
            HeadsetAction.Next -> R.string.settings_hb_action_next
            HeadsetAction.Previous -> R.string.settings_hb_action_previous
            HeadsetAction.Nothing -> R.string.settings_nothing
        }

private val NextButtonAction.label: Int
    get() =
        when (this) {
            NextButtonAction.Next -> R.string.settings_hb_action_next
            NextButtonAction.Previous -> R.string.settings_hb_action_previous
            NextButtonAction.SeekForward -> R.string.settings_hb_seek_forward
            NextButtonAction.Nothing -> R.string.settings_nothing
        }

private val PreviousButtonAction.label: Int
    get() =
        when (this) {
            PreviousButtonAction.Previous -> R.string.settings_hb_action_previous
            PreviousButtonAction.Next -> R.string.settings_hb_action_next
            PreviousButtonAction.SeekBack -> R.string.settings_hb_seek_back
            PreviousButtonAction.Nothing -> R.string.settings_nothing
        }
