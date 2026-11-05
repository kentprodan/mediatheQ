package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Genre

/** "12 albums · 80 tracks" (a zero count is left out). */
@Composable
fun genreCounts(genre: Genre): String =
    listOfNotNull(
        genre.albumCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.core_ui_album_count, it, it) },
        genre.trackCount.takeIf { it > 0 }?.let { formatTrackCount(it) },
    ).joinToString(" · ")

/** A genre in a list (#130): its first album's cover (or the genre icon), name and counts. */
@Composable
fun GenreRow(
    genre: Genre,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverRow(genre.name, genre.artwork, MqIcons.Genres, onClick, modifier, subtitle = genreCounts(genre))
}

/** A genre in a grid (#130): a square cover (or the genre icon on a tonal square), name and counts. */
@Composable
fun GenreCard(
    genre: Genre,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(bottom = MediatheqSpacing.s),
    ) {
        val artwork = genre.artwork
        if (artwork != null) {
            MqCover(artwork, contentDescription = null, blurHash = artwork.blurHash)
        } else {
            Surface(
                Modifier.fillMaxWidth().aspectRatio(1f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(MqIcons.Genres, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            genre.name,
            Modifier.fillMaxWidth().padding(top = MediatheqSpacing.s),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            genreCounts(genre),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
