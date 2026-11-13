package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.ui.description
import io.github.kentprodan.mediatheq.core.ui.icon
import io.github.kentprodan.mediatheq.core.ui.label

/** Settings -> Interface: the App mode row (ADR 0051), with the mode in effect. */
@Composable
internal fun AppModeGroup(
    mode: AppMode,
    onOpen: () -> Unit,
) {
    Group(R.string.settings_app_mode_group) {
        item { shapes ->
            SegmentedListItem(
                onClick = onOpen,
                shapes = shapes,
                colors = settingsColors(),
                supportingContent = { Text(stringResource(mode.label)) },
                trailingContent = { Icon(mode.icon, contentDescription = null) },
            ) { Text(stringResource(R.string.settings_app_mode)) }
        }
    }
}

/** Settings -> Interface -> App mode: Music, Video or Both, one sentence each; applies at once. */
internal fun LazyListScope.appModeGroups(
    mode: AppMode,
    onAction: (SettingsAction) -> Unit,
) {
    item(key = "app_mode_choices") {
        Group(R.string.settings_app_mode_group) {
            AppMode.entries.forEach { choice ->
                item { shapes ->
                    SegmentedListItem(
                        onClick = { onAction(SettingsAction.SetAppMode(choice)) },
                        shapes = shapes,
                        colors = settingsColors(),
                        modifier =
                            Modifier.semantics {
                                role = Role.RadioButton
                                selected = choice == mode
                            },
                        leadingContent = { Icon(choice.icon, contentDescription = null) },
                        supportingContent = { Text(stringResource(choice.description)) },
                        trailingContent =
                            if (choice == mode) {
                                { Icon(MqIcons.Check, contentDescription = null) }
                            } else {
                                null
                            },
                    ) { Text(stringResource(choice.label)) }
                }
            }
        }
    }
}
