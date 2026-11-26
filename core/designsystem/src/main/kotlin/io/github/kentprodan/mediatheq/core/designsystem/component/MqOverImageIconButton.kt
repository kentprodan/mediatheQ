package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A top bar icon button over a page's image (#681): a dim round backing and a white icon, so the button reads on a
 * light cover too. [overImage] is 1 while the image is under the bar and goes to 0 as the bar collapses onto its own
 * background, where the usual colors return.
 */
@Composable
fun overImageIconButtonColors(overImage: Float): IconButtonColors =
    IconButtonDefaults.iconButtonColors(
        containerColor = Color.Black.copy(alpha = SCRIM_ALPHA * overImage),
        contentColor = lerp(MaterialTheme.colorScheme.onSurface, Color.White, overImage),
    )

private const val SCRIM_ALPHA = 0.4f
