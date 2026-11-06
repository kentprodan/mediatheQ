package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
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
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** A choice dialog (component map: one row per option, a check on the selected one; a tap applies and closes). */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    MqDialog(
        title = title,
        confirmLabel = null,
        onConfirm = {},
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
    ) {
        ChoiceRows(options, selected, label) {
            onSelect(it)
            onDismiss()
        }
    }
}

/** [ChoiceDialog]'s content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
internal fun <T> ChoiceDialogContent(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
) {
    MqDialogContent(title, null, {}, stringResource(R.string.settings_cancel), {}) {
        ChoiceRows(options, selected, label) {}
    }
}

@Composable
private fun <T> ChoiceRows(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        options.forEach { option ->
            val isSelected = option == selected
            ListItem(
                headlineContent = { Text(label(option)) },
                trailingContent = if (isSelected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.selectable(isSelected, role = Role.RadioButton) { onSelect(option) },
            )
        }
    }
}
