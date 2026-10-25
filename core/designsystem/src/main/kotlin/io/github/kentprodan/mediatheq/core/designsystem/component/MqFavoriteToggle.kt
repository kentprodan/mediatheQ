package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.R
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** The heart: an icon toggle whose shape morphs when checked, with toggle haptics (expressive-design, section 5). */
@Composable
fun MqFavoriteToggle(
    isFavorite: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    FilledTonalIconToggleButton(
        checked = isFavorite,
        onCheckedChange = {
            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onToggle(it)
        },
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = modifier,
    ) {
        // The filled heart marks the checked state, so it is not shown by color alone.
        Icon(
            if (isFavorite) MqIcons.FavoriteFilled else MqIcons.Favorite,
            contentDescription = stringResource(R.string.mq_favorite),
        )
    }
}
