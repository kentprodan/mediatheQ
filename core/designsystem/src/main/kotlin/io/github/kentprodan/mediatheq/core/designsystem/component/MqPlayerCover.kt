package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The large Now Playing cover: square, `extraLarge` corners, at most 360 dp wide (expressive-design skill, section 5).
 * Give it a weight in a column so it shrinks before the controls do (e.g. at fontScale 2.0).
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
        modifier = modifier.widthIn(max = MaxWidth).aspectRatio(1f),
        blurHash = blurHash,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

private val MaxWidth = 360.dp
