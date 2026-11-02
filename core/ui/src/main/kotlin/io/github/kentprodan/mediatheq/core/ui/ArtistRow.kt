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
    /** Long press, e.g. to start selecting; `null` for none. */
    onLongClick: (() -> Unit)? = null,
    /** In a multi-select list: whether this row is selected. `null` outside selection. */
    selected: Boolean? = null,
) {
    CoverRow(
        artist.name,
        artist.artwork,
        MqIcons.Person,
        onClick,
        modifier,
        coverShape = CircleShape,
        isFavorite = artist.userData.isFavorite,
        onLongClick = onLongClick,
        selected = selected,
    )
}
