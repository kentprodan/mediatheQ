package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.ColumnScope
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
    /** A third line, e.g. a download's size and duration. */
    details: String? = null,
    menuItems: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    CoverRow(
        title = playlist.name,
        artwork = playlist.artwork,
        placeholderIcon = MqIcons.QueueMusic,
        onClick = onClick,
        modifier = modifier,
        details = details,
        menuItems = menuItems,
        subtitle = formatTrackCount(playlist.trackCount),
    )
}
