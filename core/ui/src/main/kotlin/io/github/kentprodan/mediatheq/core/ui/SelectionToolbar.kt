package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** Space a list leaves at its bottom while the selection toolbar floats over it. */
val SelectionToolbarSpace = FloatingToolbarDefaults.ContainerSize + FloatingToolbarDefaults.ScreenOffset * 2

/**
 * The floating toolbar of a media list while selecting (#123): Clear, "n selected", Play, Play next and Add to queue,
 * then a More menu with Add to playlist, Download, Favorite and Select all. [extraActions] go before More (playlist
 * edit's Remove). With nothing selected (playlist Edit) the commands are off; Select all stays.
 */
@Composable
fun SelectionToolbar(
    count: Int,
    onCommand: (SelectionCommand) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    var more by remember { mutableStateOf(false) }
    val enabled = count > 0
    HorizontalFloatingToolbar(expanded = true, modifier = modifier.padding(FloatingToolbarDefaults.ScreenOffset)) {
        IconButton(onClick = onClear) {
            Icon(MqIcons.Close, contentDescription = stringResource(R.string.core_ui_clear_selection))
        }
        Text(
            pluralStringResource(R.plurals.core_ui_selected, count, count),
            Modifier.align(Alignment.CenterVertically).padding(horizontal = MediatheqSpacing.s),
            style = MaterialTheme.typography.labelLarge,
        )
        CommandButton(MqIcons.PlayArrow, R.string.core_ui_play, enabled) { onCommand(SelectionCommand.Play) }
        CommandButton(MqIcons.PlayNext, R.string.core_ui_play_next, enabled) { onCommand(SelectionCommand.PlayNext) }
        CommandButton(
            MqIcons.AddToQueue,
            R.string.core_ui_add_to_queue,
            enabled,
        ) { onCommand(SelectionCommand.AddToQueue) }
        extraActions()
        Box {
            IconButton(onClick = { more = true }) {
                Icon(MqIcons.MoreVert, contentDescription = stringResource(R.string.core_ui_more_actions))
            }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                listOf(
                    Triple(MqIcons.AddToPlaylist, R.string.core_ui_add_to_playlist, SelectionCommand.AddToPlaylist),
                    Triple(MqIcons.Download, R.string.core_ui_download, SelectionCommand.Download),
                    Triple(MqIcons.Favorite, R.string.core_ui_favorite, SelectionCommand.Favorite),
                ).forEach { (icon, label, command) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        leadingIcon = { Icon(icon, contentDescription = null) },
                        onClick = {
                            more = false
                            onCommand(command)
                        },
                        enabled = enabled,
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.core_ui_select_all)) },
                    leadingIcon = { Icon(MqIcons.Checklist, contentDescription = null) },
                    onClick = {
                        more = false
                        onSelectAll()
                    },
                )
            }
        }
    }
}

@Composable
private fun CommandButton(
    icon: ImageVector,
    label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = stringResource(label)) }
}
