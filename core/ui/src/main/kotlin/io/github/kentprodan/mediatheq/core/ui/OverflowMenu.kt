package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/**
 * A "more" button that opens a menu of [items]; each item calls `close` after its action. Up to five actions; more
 * belong in a bottom sheet (expressive-design component map).
 */
@Composable
fun OverflowMenu(
    contentDescription: String,
    modifier: Modifier = Modifier,
    items: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(modifier) {
        IconButton(
            onClick = {
                expanded = true
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            },
        ) {
            Icon(MqIcons.MoreVert, contentDescription = contentDescription)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { items { expanded = false } }
    }
}

/** "Play next" and "Add to queue", for track and album menus. */
@Composable
fun ColumnScope.QueueMenuItems(
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    close: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.core_ui_play_next)) },
        leadingIcon = { Icon(MqIcons.PlayNext, contentDescription = null) },
        onClick = {
            onPlayNext()
            close()
        },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.core_ui_add_to_queue)) },
        leadingIcon = { Icon(MqIcons.AddToQueue, contentDescription = null) },
        onClick = {
            onAddToQueue()
            close()
        },
    )
}

/** "Add to playlist", for track and album menus. */
@Composable
fun AddToPlaylistMenuItem(
    onClick: () -> Unit,
    close: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.core_ui_add_to_playlist)) },
        leadingIcon = { Icon(MqIcons.AddToPlaylist, contentDescription = null) },
        onClick = {
            onClick()
            close()
        },
    )
}

/** "Add to favorites" or "Remove from favorites", for track menus. */
@Composable
fun FavoriteMenuItem(
    isFavorite: Boolean,
    onToggle: (Boolean) -> Unit,
    close: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(stringResource(if (isFavorite) R.string.core_ui_remove_favorite else R.string.core_ui_add_favorite))
        },
        leadingIcon = {
            Icon(if (isFavorite) MqIcons.FavoriteFilled else MqIcons.Favorite, contentDescription = null)
        },
        onClick = {
            onToggle(!isFavorite)
            close()
        },
    )
}

/** "Download", for track, album and playlist menus. */
@Composable
fun DownloadMenuItem(
    onClick: () -> Unit,
    close: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.core_ui_download)) },
        leadingIcon = { Icon(MqIcons.Download, contentDescription = null) },
        onClick = {
            onClick()
            close()
        },
    )
}

/** "Remove download": deletes the copy on this device only, never anything on the server (jellyfin R5). */
@Composable
fun RemoveDownloadMenuItem(
    onClick: () -> Unit,
    close: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.core_ui_remove_download)) },
        leadingIcon = { Icon(MqIcons.Delete, contentDescription = null) },
        onClick = {
            onClick()
            close()
        },
    )
}
