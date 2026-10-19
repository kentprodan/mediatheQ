package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** How a [MqFilterBar] looks; the user picks one (ADR 0014). */
enum class MqFilterBarStyle { Chips, ConnectedButtons }

/**
 * Single choice among a few views (e.g. Albums / Artists / Playlists / Songs). Chips scroll sideways, so they fit at any
 * font size; connected buttons share the width and shorten long labels. The selected item also shows a check mark or a
 * different shape, so selection is never shown by color alone.
 */
@Composable
fun MqFilterBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: MqFilterBarStyle = MqFilterBarStyle.Chips,
) {
    when (style) {
        MqFilterBarStyle.Chips -> {
            Row(
                modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MediatheqSpacing.l)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
            ) {
                labels.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    FilterChip(
                        selected = selected,
                        onClick = { onSelect(index) },
                        label = { Text(label, maxLines = 1) },
                        leadingIcon =
                            if (selected) {
                                {
                                    Icon(
                                        MqIcons.Check,
                                        contentDescription = null,
                                        Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            } else {
                                null
                            },
                        modifier = Modifier.semantics { role = Role.RadioButton },
                    )
                }
            }
        }

        MqFilterBarStyle.ConnectedButtons -> {
            Row(
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = MediatheqSpacing.l)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                labels.forEachIndexed { index, label ->
                    ToggleButton(
                        checked = index == selectedIndex,
                        onCheckedChange = { onSelect(index) },
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        shapes =
                            when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
