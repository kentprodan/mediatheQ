package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/**
 * An artist's picture in a circle (#531: the platform's shape for people; expressive shapes stay on the Play morph).
 * Without [model] it shows a person icon. [model] is anything [MqCover] takes.
 */
@Composable
fun MqArtistAvatar(
    model: Any?,
    modifier: Modifier = Modifier,
    blurHash: String? = null,
) {
    val shape = CircleShape
    if (model != null) {
        MqCover(model, contentDescription = null, modifier, blurHash, shape)
    } else {
        Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                MqIcons.Person,
                contentDescription = null,
                Modifier.fillMaxSize(PLACEHOLDER_ICON_FRACTION),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val PLACEHOLDER_ICON_FRACTION = 0.4f
