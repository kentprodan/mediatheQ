package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedIconButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import androidx.tv.material3.WideButtonDefaults

/**
 * The guideline's button types (#519): [Filled] for the primary action (Play), [Outlined] for the secondary ones
 * (Trailer, From the start). The first button of a group is the primary one.
 */
enum class MqTvButtonStyle { Filled, Outlined }

/**
 * A text button (ADR 0037, #519): fully rounded, `labelLarge`, one line, an optional icon; grows to 1.1 with focus
 * (the tv-material default).
 */
@Composable
fun MqTvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    style: MqTvButtonStyle = MqTvButtonStyle.Filled,
) {
    val content: @Composable () -> Unit = {
        icon?.let {
            Icon(it, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ICON_SPACING))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (style) {
        MqTvButtonStyle.Filled -> Button(onClick, modifier, enabled = enabled) { content() }
        MqTvButtonStyle.Outlined -> OutlinedButton(onClick, modifier, enabled = enabled) { content() }
    }
}

/**
 * An icon button (#519): Favorite, Watched, More. [outlined] for the secondary ones next to text buttons. The icon
 * carries the state (a filled heart), [contentDescription] names the action.
 */
@Composable
fun MqTvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = true,
) {
    if (outlined) {
        OutlinedIconButton(onClick, modifier, enabled = enabled) { Icon(icon, contentDescription) }
    } else {
        IconButton(onClick, modifier, enabled = enabled) { Icon(icon, contentDescription) }
    }
}

/** A wide button (#519): a settings-like action with a [subtitle] (the current value), 12 dp corners. */
@Composable
fun MqTvWideButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    WideButton(
        onClick = onClick,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        shape = WideButtonDefaults.shape(shape = BUTTON_CORNERS),
    )
}

/**
 * An image button (#519): seasons, cast. The image under a gradient scrim with the [label] (and [supporting] line) at
 * its foot, 12 dp corners, 1.1 with focus. TalkBack reads the label and the line as one name.
 */
@Composable
fun MqTvImageButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    image: @Composable () -> Unit,
) {
    val name = listOfNotNull(label, supporting).joinToString(", ")
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = name },
        shape = ClickableSurfaceDefaults.shape(shape = BUTTON_CORNERS),
        scale = ClickableSurfaceDefaults.scale(focusedScale = FOCUSED_SCALE),
    ) {
        Box(Modifier.fillMaxSize()) {
            image()
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to SCRIM)))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp).clearAndSetSemantics {}) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                supporting?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = SUPPORTING_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val ICON_SPACING = 8.dp
private val BUTTON_CORNERS = RoundedCornerShape(12.dp)
private const val FOCUSED_SCALE = 1.1f
private val SCRIM = Color.Black.copy(alpha = 0.75f)
private const val SUPPORTING_ALPHA = 0.8f
