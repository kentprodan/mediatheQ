package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.Playlist

/** A playlist in a list: cover, name and track count. */
@Composable
fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverRow(
        title = playlist.name,
        artwork = playlist.artwork,
        placeholderIcon = MqIcons.QueueMusic,
        onClick = onClick,
        modifier = modifier,
        subtitle = formatTrackCount(playlist.trackCount),
    )
}
