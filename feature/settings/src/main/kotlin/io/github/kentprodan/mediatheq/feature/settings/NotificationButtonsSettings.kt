package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.data.NotificationButtonsPreferences
import io.github.kentprodan.mediatheq.core.data.SessionButtonAction

/** The action dialogs of Settings -> Playback -> Notification buttons (#139): Action 1, 2, 3. */
enum class NotificationActionDialog { Action1, Action2, Action3 }

/** Settings -> Playback -> Notification buttons (#139): the notification, the lock screen and the car. */
internal fun LazyListScope.notificationButtonsGroups(
    buttons: NotificationButtonsPreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (NotificationActionDialog) -> Unit,
    onRestore: () -> Unit,
) {
    val set = { it: NotificationButtonsPreferences -> onAction(SettingsAction.SetNotificationButtons(it)) }
    item(key = "notification_buttons") {
        Group(R.string.settings_nb_buttons) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_nb_previous),
                    stringResource(R.string.settings_nb_skip_summary),
                    buttons.showPrevious,
                ) { set(buttons.copy(showPrevious = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_nb_next),
                    stringResource(R.string.settings_nb_skip_summary),
                    buttons.showNext,
                ) { set(buttons.copy(showNext = it)) }
            }
            NotificationActionDialog.entries.forEach { dialog ->
                item { shapes ->
                    DialogItem(
                        shapes,
                        stringResource(R.string.settings_nb_action, dialog.ordinal + 1),
                        stringResource(buttons.actionOf(dialog).label),
                    ) { onOpenDialog(dialog) }
                }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

private fun NotificationButtonsPreferences.actionOf(dialog: NotificationActionDialog): SessionButtonAction =
    when (dialog) {
        NotificationActionDialog.Action1 -> action1
        NotificationActionDialog.Action2 -> action2
        NotificationActionDialog.Action3 -> action3
    }

private fun NotificationButtonsPreferences.with(
    dialog: NotificationActionDialog,
    action: SessionButtonAction,
): NotificationButtonsPreferences =
    when (dialog) {
        NotificationActionDialog.Action1 -> copy(action1 = action)
        NotificationActionDialog.Action2 -> copy(action2 = action)
        NotificationActionDialog.Action3 -> copy(action3 = action)
    }

@Composable
internal fun NotificationActionDialogHost(
    dialog: NotificationActionDialog,
    buttons: NotificationButtonsPreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) = ChoiceDialog(
    stringResource(R.string.settings_nb_action, dialog.ordinal + 1),
    SessionButtonAction.entries,
    buttons.actionOf(dialog),
    { stringResource(it.label) },
    { onAction(SettingsAction.SetNotificationButtons(buttons.with(dialog, it))) },
    onDismiss,
)

/** An action dialog's content, for screenshot tests. */
@Composable
internal fun NotificationActionDialogContent(
    dialog: NotificationActionDialog,
    buttons: NotificationButtonsPreferences,
) = ChoiceDialogContent(
    stringResource(R.string.settings_nb_action, dialog.ordinal + 1),
    SessionButtonAction.entries,
    buttons.actionOf(dialog),
) { stringResource(it.label) }

private val SessionButtonAction.label: Int
    get() =
        when (this) {
            SessionButtonAction.Favorite -> R.string.settings_nb_favorite
            SessionButtonAction.Repeat -> R.string.settings_nb_repeat
            SessionButtonAction.Shuffle -> R.string.settings_nb_shuffle
            SessionButtonAction.Nothing -> R.string.settings_nothing
        }
