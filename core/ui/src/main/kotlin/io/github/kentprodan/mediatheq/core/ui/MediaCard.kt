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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.designsystem.component.MqArtistAvatar
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Playlist

/** An album artist in a Home row or grid (#127): the artist's picture in its accent shape, the name centered. */
@Composable
fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A play button on the picture (#182); `null` for none. */
    onPlay: (() -> Unit)? = null,
) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(bottom = MediatheqSpacing.s),
    ) {
        Box {
            MqArtistAvatar(artist.artwork, blurHash = artist.artwork?.blurHash)
            onPlay?.let { CoverPlay(artist.name, it) }
        }
        Text(
            artist.name,
            Modifier.fillMaxWidth().padding(top = MediatheqSpacing.s),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A playlist in a Home row or grid (#127): square cover, its name and track count. */
@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The track count under the name; off in a Home section that hides subtitles (#182). */
    showSubtitle: Boolean = true,
    /** A play button on the cover (#182); `null` for none. */
    onPlay: (() -> Unit)? = null,
) {
    val textAlign = if (LocalMediaListStyle.current.centerGridText) TextAlign.Center else TextAlign.Start
    Column(
        modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(bottom = MediatheqSpacing.s),
    ) {
        Box {
            MqCover(playlist.artwork, contentDescription = null, blurHash = playlist.artwork?.blurHash)
            onPlay?.let { CoverPlay(playlist.name, it) }
        }
        Text(
            playlist.name,
            Modifier.fillMaxWidth().padding(top = MediatheqSpacing.s),
            style = MaterialTheme.typography.titleSmall,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showSubtitle) {
            Text(
                pluralStringResource(R.plurals.core_ui_track_count, playlist.trackCount, playlist.trackCount),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = textAlign,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
