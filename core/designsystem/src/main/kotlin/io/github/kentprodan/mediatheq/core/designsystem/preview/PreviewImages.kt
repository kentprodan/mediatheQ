package io.github.kentprodan.mediatheq.core.designsystem.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/** A valid BlurHash for previews and screenshot tests. */
const val PREVIEW_BLUR_HASH = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"

/** A deterministic "loaded cover" for `MqCover` in previews and screenshot tests (no network). */
@Composable
fun rememberPreviewCover(): Painter {
    val start = MaterialTheme.colorScheme.primary
    val end = MaterialTheme.colorScheme.tertiary
    return remember(start, end) { GradientPainter(start, end) }
}

private class GradientPainter(
    private val start: Color,
    private val end: Color,
) : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRect(Brush.linearGradient(listOf(start, end)))
    }
}
