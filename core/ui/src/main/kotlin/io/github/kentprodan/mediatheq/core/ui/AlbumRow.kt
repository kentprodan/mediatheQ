package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.Album

/** An album as a list row: cover, title and album artists (search results; the Library shows [AlbumCard]s). */
@Composable
fun AlbumRow(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverRow(
        title = album.title,
        artwork = album.artwork,
        placeholderIcon = MqIcons.MusicNote,
        onClick = onClick,
        modifier = modifier,
        subtitle = album.albumArtists.joinToString { it.name },
    )
}
