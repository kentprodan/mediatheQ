package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvCard
import io.github.kentprodan.mediatheq.core.model.Artwork

/**
 * A cover card for a TV row (#236): an album, artist, playlist or track with its [artwork], [title] and [subtitle].
 * Focus scales the whole card (tv-material); [onLongClick] opens the item's menu.
 */
@Composable
fun MqTvAlbumCard(
    title: String,
    artwork: Artwork?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    placeholder: ImageVector = MqTvIcons.MusicNote,
    onLongClick: (() -> Unit)? = null,
) {
    MqTvCard(title, onClick, modifier, subtitle, onLongClick) { MqTvCover(artwork, placeholder) }
}

/** The [artwork] filling its box, over a tinted [placeholder] icon that shows until (or instead of) the image. */
@Composable
fun MqTvCover(
    artwork: Artwork?,
    placeholder: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            placeholder,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (artwork != null) {
            AsyncImage(artwork, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
