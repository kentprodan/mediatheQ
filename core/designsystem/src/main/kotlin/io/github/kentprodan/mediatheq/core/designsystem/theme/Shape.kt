package io.github.kentprodan.mediatheq.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** How round the theme's corners are (#137): the M3 shape scale times [factor]. */
enum class MqCornerScale(
    val factor: Float,
) {
    Small(0.5f),
    Normal(1f),
    Large(1.5f),
}

/**
 * The M3 shape scale (material3 1.5 tokens: 4, 8, 12, 16, 20, 28, 32, 48 dp) times [scale]'s factor. The one place to
 * adjust corners: every component that reads `MaterialTheme.shapes` follows; full-round shapes stay round.
 */
fun mediatheqShapes(scale: MqCornerScale): Shapes {
    fun corner(dp: Int) = RoundedCornerShape((dp * scale.factor).dp)
    return Shapes(
        extraSmall = corner(4),
        small = corner(8),
        medium = corner(12),
        large = corner(16),
        extraLarge = corner(28),
        largeIncreased = corner(20),
        extraLargeIncreased = corner(32),
        extraExtraLarge = corner(48),
    )
}

/** The Normal shape scale, for previews and code outside [MediatheqTheme]. */
val MediatheqShapes = mediatheqShapes(MqCornerScale.Normal)
