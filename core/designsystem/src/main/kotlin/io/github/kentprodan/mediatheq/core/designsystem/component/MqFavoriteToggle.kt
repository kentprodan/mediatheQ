package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.R
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/**
 * The heart: a standard [MqIconToggle] (no container, #531) with the filled heart in the accent when checked, so the
 * state is not shown by color alone.
 */
@Composable
fun MqFavoriteToggle(
    isFavorite: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** In a list, name the item ("Favorite Morning Light"). */
    contentDescription: String = stringResource(R.string.mq_favorite),
) {
    MqIconToggle(
        checked = isFavorite,
        onClick = { onToggle(!isFavorite) },
        icon = if (isFavorite) MqIcons.FavoriteFilled else MqIcons.Favorite,
        contentDescription = contentDescription,
        modifier = modifier,
        standard = true,
    )
}
