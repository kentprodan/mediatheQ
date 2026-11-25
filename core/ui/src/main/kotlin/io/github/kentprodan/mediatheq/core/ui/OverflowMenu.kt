package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.designsystem.component.MqStarRating
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import kotlinx.coroutines.launch

/**
 * A "more" button that opens a sheet from the bottom (#681) with [header] (e.g. the rating) over [items]; each item
 * calls `close` after its action, which slides the sheet away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverflowMenu(
    contentDescription: String,
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    items: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            expanded = true
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        },
        modifier = modifier,
    ) {
        Icon(MqIcons.MoreVert, contentDescription = contentDescription)
    }
    if (expanded) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val close: () -> Unit = { scope.launch { sheet.hide() }.invokeOnCompletion { expanded = false } }
        ModalBottomSheet(onDismissRequest = { expanded = false }, sheetState = sheet) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = MediatheqSpacing.l)) {
                header?.let {
                    it()
                    HorizontalDivider(Modifier.padding(vertical = MediatheqSpacing.s))
                }
                items(close)
            }
        }
    }
}

/**
 * One choice in a [OverflowMenu] sheet: the icon, the label, full width (a menu item stops at 280 dp, too narrow for a
 * sheet).
 */
@Composable
fun MqMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.onSurface.let { if (enabled) it else it.copy(alpha = DISABLED_ALPHA) }
    CompositionLocalProvider(LocalContentColor provides color) {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = ITEM_HEIGHT)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = MediatheqSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.l),
        ) {
            leadingIcon?.invoke()
            Box(Modifier.weight(1f)) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() }
            }
            trailingIcon?.invoke()
        }
    }
}

/** The rating at the top of a More sheet (#681): the only place an album's or an artist's stars show. */
@Composable
fun ColumnScope.RatingSheetHeader(
    halfStars: Int?,
    onRate: (Int?) -> Unit,
    allowHalf: Boolean,
) {
    MqStarRating(halfStars, onRate, Modifier.align(Alignment.CenterHorizontally), allowHalf = allowHalf)
}

private val ITEM_HEIGHT = 56.dp
private const val DISABLED_ALPHA = 0.38f

/** "Play next" and "Add to queue", for track and album menus. */
@Composable
fun ColumnScope.QueueMenuItems(
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    close: () -> Unit,
) {
    MqMenuItem(
        text = { Text(stringResource(R.string.core_ui_play_next)) },
        leadingIcon = { Icon(MqIcons.PlayNext, contentDescription = null) },
        onClick = {
            onPlayNext()
            close()
        },
    )
    MqMenuItem(
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
    MqMenuItem(
        text = { Text(stringResource(R.string.core_ui_add_to_playlist)) },
        leadingIcon = { Icon(MqIcons.AddToPlaylist, contentDescription = null) },
        onClick = {
            onClick()
            close()
        },
    )
}

/** "Start radio" (#379): a mix like this track, album, artist, genre or playlist, from the server. */
@Composable
fun StartRadioMenuItem(
    onClick: () -> Unit,
    close: () -> Unit,
) {
    MqMenuItem(
        text = { Text(stringResource(R.string.core_ui_start_radio)) },
        leadingIcon = { Icon(MqIcons.Radio, contentDescription = null) },
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
    MqMenuItem(
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
    MqMenuItem(
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
    MqMenuItem(
        text = { Text(stringResource(R.string.core_ui_remove_download)) },
        leadingIcon = { Icon(MqIcons.Delete, contentDescription = null) },
        onClick = {
            onClick()
            close()
        },
    )
}
