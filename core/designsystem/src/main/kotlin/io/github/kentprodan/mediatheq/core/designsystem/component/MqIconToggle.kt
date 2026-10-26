package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * An icon toggle that is round when off and squarish when [checked], with toggle haptics (expressive-design,
 * section 5): ToggleOn when the tap [turnsOn], ToggleOff otherwise (a mode with more than two values passes it).
 * [stateDescription] replaces the on/off readout for TalkBack.
 */
@Composable
fun MqIconToggle(
    checked: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    turnsOn: Boolean = !checked,
    stateDescription: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(if (turnsOn) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onClick()
        },
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier =
            if (stateDescription != null) {
                modifier.semantics { this.stateDescription = stateDescription }
            } else {
                modifier
            },
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
