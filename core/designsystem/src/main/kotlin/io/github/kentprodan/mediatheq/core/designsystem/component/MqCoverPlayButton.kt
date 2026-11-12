package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** A large round play button over Now Playing's cover while paused (#126, an option of Now playing screens). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MqCoverPlayButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    /** The small size, for a card's cover in a row (#182); the large one is Now Playing's. */
    small: Boolean = false,
) {
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier =
            modifier.size(
                if (small) IconButtonDefaults.smallContainerSize() else IconButtonDefaults.largeContainerSize(),
            ),
    ) {
        Icon(
            MqIcons.PlayFilled,
            contentDescription = contentDescription,
            Modifier.size(IconButtonDefaults.largeIconSize),
        )
    }
}
