package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.model.Artwork

/** List row with a small cover (or a placeholder icon), a title and an optional second line. */
@Composable
internal fun CoverRow(
    title: String,
    artwork: Artwork?,
    placeholderIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    coverShape: Shape = MaterialTheme.shapes.small,
) {
    ListItem(
        onClick = onClick,
        modifier = modifier,
        leadingContent = { RowCover(artwork, placeholderIcon, coverShape) },
        supportingContent = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A row-sized cover (48 dp); [placeholderIcon] shows when the item has no image. */
@Composable
fun RowCover(
    artwork: Artwork?,
    placeholderIcon: ImageVector,
    shape: Shape = MaterialTheme.shapes.small,
) {
    if (artwork != null) {
        MqCover(artwork, contentDescription = null, Modifier.size(ROW_COVER_SIZE), artwork.blurHash, shape)
    } else {
        Box(Modifier.size(ROW_COVER_SIZE), contentAlignment = Alignment.Center) {
            Icon(placeholderIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val ROW_COVER_SIZE = 48.dp
