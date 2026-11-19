package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The large Now Playing cover: square, `extraLarge` corners, as wide as the screen's content (#532), else as tall as
 * the room left. Give it a weight in a column so it shrinks before the controls do (e.g. at fontScale 2.0).
 */
@Composable
fun MqPlayerCover(
    model: Any?,
    modifier: Modifier = Modifier,
    blurHash: String? = null,
) {
    MqCover(
        model = model,
        contentDescription = null,
        modifier = modifier.aspectRatio(1f),
        blurHash = blurHash,
        shape = MaterialTheme.shapes.extraLarge,
    )
}
