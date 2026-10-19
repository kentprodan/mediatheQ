package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.Artist

/** An artist in a list: round picture and name. */
@Composable
fun ArtistRow(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverRow(artist.name, artist.artwork, MqIcons.Person, onClick, modifier, coverShape = CircleShape)
}
