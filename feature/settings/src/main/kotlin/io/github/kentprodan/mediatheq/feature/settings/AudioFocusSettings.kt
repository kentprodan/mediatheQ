package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.data.AudioFocusPreferences
import io.github.kentprodan.mediatheq.core.data.ShortFocusAction

/** Settings -> Playback -> Audio focus (#139): every change applies at once. */
internal fun LazyListScope.audioFocusGroups(
    focus: AudioFocusPreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenChoice: () -> Unit,
    onRestore: () -> Unit,
) {
    item(key = "audio_focus") {
        Group(R.string.settings_af_interruptions) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_af_short),
                    stringResource(focus.shortAction.label),
                    onClick = onOpenChoice,
                )
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_af_no_resume),
                    stringResource(R.string.settings_af_no_resume_summary),
                    focus.noResumeAfterLongLoss,
                ) { onAction(SettingsAction.SetAudioFocus(focus.copy(noResumeAfterLongLoss = it))) }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

/** The short interruption dialog. */
@Composable
internal fun ShortFocusDialog(
    focus: AudioFocusPreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) = ChoiceDialog(
    stringResource(R.string.settings_af_short),
    ShortFocusAction.entries,
    focus.shortAction,
    { stringResource(it.label) },
    { onAction(SettingsAction.SetAudioFocus(focus.copy(shortAction = it))) },
    onDismiss,
)

/** [ShortFocusDialog]'s content, for screenshot tests. */
@Composable
internal fun ShortFocusDialogContent(focus: AudioFocusPreferences) =
    ChoiceDialogContent(
        stringResource(R.string.settings_af_short),
        ShortFocusAction.entries,
        focus.shortAction,
    ) { stringResource(it.label) }

private val ShortFocusAction.label: Int
    get() =
        when (this) {
            ShortFocusAction.Duck -> R.string.settings_af_duck
            ShortFocusAction.Pause -> R.string.settings_af_pause
            ShortFocusAction.Nothing -> R.string.settings_nothing
        }
