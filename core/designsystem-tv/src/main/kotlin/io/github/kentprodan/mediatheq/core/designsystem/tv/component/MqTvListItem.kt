package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.tv.material3.Checkbox
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Switch
import androidx.tv.material3.Text

/**
 * The guideline selection controls of a list row (#523): one choice of several ([Radio]), an option among others that
 * can all be on ([Checkbox]), a setting that is on or off ([Switch]). The row's [MqTvListItem] `selected` is its state,
 * so it never rests on the highlight color alone.
 */
enum class MqTvControl { Radio, Checkbox, Switch }

/**
 * A TV list row (ADR 0037): headline, optional supporting text, leading icon and [trailing] content; [selected] marks
 * the current one, and a [control] (#523) shows it as a radio button, checkbox or switch at the end of the row, which
 * select toggles.
 */
@Composable
fun MqTvListItem(
    headline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    /** Long-press of select: the item's menu. */
    onLongClick: (() -> Unit)? = null,
    control: MqTvControl? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val state =
        control?.let {
            Modifier.semantics {
                role =
                    when (it) {
                        MqTvControl.Radio -> Role.RadioButton
                        MqTvControl.Checkbox -> Role.Checkbox
                        MqTvControl.Switch -> Role.Switch
                    }
                toggleableState = ToggleableState(selected)
            }
        } ?: Modifier
    ListItem(
        // A switch shows its state itself; the row is not lit up while it is on.
        selected = selected && control != MqTvControl.Switch,
        onClick = onClick,
        headlineContent = { Text(headline) },
        modifier = modifier.then(state),
        enabled = enabled,
        onLongClick = onLongClick,
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent =
            when (control) {
                // The row handles select: the control only shows the state.
                MqTvControl.Radio -> {
                    { RadioButton(selected = selected, onClick = null, enabled = enabled) }
                }

                MqTvControl.Checkbox -> {
                    { Checkbox(checked = selected, onCheckedChange = null, enabled = enabled) }
                }

                MqTvControl.Switch -> {
                    { Switch(checked = selected, onCheckedChange = null, enabled = enabled) }
                }

                null -> {
                    trailing
                }
            },
    )
}
