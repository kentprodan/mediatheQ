package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.Artwork

/**
 * List row with a small cover (or a placeholder icon), a title, an optional second line and an optional third one
 * ([details], e.g. a download's size and duration), and an optional overflow menu ([menuItems]).
 */
@Composable
internal fun CoverRow(
    title: String,
    artwork: Artwork?,
    placeholderIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    coverShape: Shape = MaterialTheme.shapes.small,
    details: String? = null,
    menuItems: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    /** In a multi-select list: whether this row is selected (a check replaces the cover). `null` outside selection. */
    selected: Boolean? = null,
) {
    val lines = listOfNotNull(subtitle, details)
    val leading: @Composable () -> Unit =
        if (selected == true) {
            { SelectedMark() }
        } else {
            { RowCover(artwork, placeholderIcon, coverShape, isFavorite, isDownloaded) }
        }
    val supporting: (@Composable () -> Unit)? =
        lines.takeIf { it.isNotEmpty() }?.let {
            { Column { it.forEach { line -> Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
        }
    // No menu while selecting: the toolbar acts on the selection.
    val trailing: (@Composable () -> Unit)? =
        menuItems?.takeIf { selected == null }?.let {
            { OverflowMenu(stringResource(R.string.core_ui_more_options, title), items = it) }
        }
    val headline: @Composable () -> Unit = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    if (selected != null) {
        ListItem(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            onLongClick = onLongClick,
            content = headline,
        )
    } else {
        ListItem(
            onClick = onClick,
            modifier = modifier,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            onLongClick = onLongClick,
            content = headline,
        )
    }
}

/** The filled check that marks a selected item in a multi-select list, sized like a row cover. */
@Composable
internal fun SelectedMark() {
    Box(Modifier.size(ROW_COVER_SIZE), contentAlignment = Alignment.Center) {
        Icon(MqIcons.CheckCircleFilled, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * A row-sized cover (48 dp); [placeholderIcon] shows when the item has no image. Favorite and downloaded marks show
 * as [LocalMediaListStyle] says.
 */
@Composable
fun RowCover(
    artwork: Artwork?,
    placeholderIcon: ImageVector,
    shape: Shape = MaterialTheme.shapes.small,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
) {
    Box(Modifier.size(ROW_COVER_SIZE), contentAlignment = Alignment.Center) {
        if (artwork != null) {
            MqCover(artwork, contentDescription = null, Modifier.size(ROW_COVER_SIZE), artwork.blurHash, shape)
        } else {
            Icon(placeholderIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CoverOverlays(isFavorite, isDownloaded, compact = true)
    }
}

private val ROW_COVER_SIZE = 48.dp
