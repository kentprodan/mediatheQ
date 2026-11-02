package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.motion.mqSharedCover
import io.github.kentprodan.mediatheq.core.designsystem.motion.sharedCoverKey
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Album

/**
 * An album in a grid or row: square cover, then title and album artists on one line each. [sharedCover] off when the
 * same album already shows elsewhere on screen: only one cover may fly into the album page.
 */
@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverModel: Any? = album.artwork,
    coverBlurHash: String? = album.artwork?.blurHash,
    sharedCover: Boolean = true,
) {
    val style = LocalMediaListStyle.current
    val textAlign = if (style.centerGridText) TextAlign.Center else TextAlign.Start
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            // Keeps the artist line clear of the rounded bottom corners of the click/ripple shape.
            .padding(bottom = MediatheqSpacing.s),
    ) {
        Box {
            MqCover(
                model = coverModel,
                contentDescription = null,
                // Flies into the album page's header cover.
                modifier = if (sharedCover) Modifier.mqSharedCover(sharedCoverKey(album.id)) else Modifier,
                blurHash = coverBlurHash,
            )
            CoverOverlays(album.userData.isFavorite, album.id in style.downloaded)
        }
        Text(
            album.title,
            Modifier.fillMaxWidth().padding(top = MediatheqSpacing.s),
            style = MaterialTheme.typography.titleSmall,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            album.albumArtists.joinToString { it.name },
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            textAlign = textAlign,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
