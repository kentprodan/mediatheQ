package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes

/**
 * A round color swatch with an outline; with [onClick] it is a selectable choice (a check marks [selected], not only
 * the color), without it a read-only sample.
 */
@Composable
fun MqColorSwatch(
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val border = BorderStroke(MediatheqSizes.swatchOutline, MaterialTheme.colorScheme.outlineVariant)
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    MqIcons.Check,
                    contentDescription = null,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                )
            }
        }
    }
    val semantics =
        Modifier.semantics {
            this.contentDescription = contentDescription
            if (onClick != null) {
                this.selected = selected
                role = Role.RadioButton
            }
        }
    if (onClick == null) {
        Surface(modifier.size(MediatheqSizes.colorSwatch).then(semantics), CircleShape, color, border = border) {
            content()
        }
    } else {
        Surface(
            onClick,
            modifier.size(MediatheqSizes.colorSwatch).then(semantics),
            shape = CircleShape,
            color = color,
            border = border,
            content = content,
        )
    }
}
