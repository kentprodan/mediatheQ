package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.kentprodan.mediatheq.core.data.TransitionsPreferences
import kotlin.math.roundToInt

/** Settings -> Playback -> Transitions (#142): fade in and fade out. Crossfade comes with #219. */
internal fun LazyListScope.transitionsGroups(
    transitions: TransitionsPreferences,
    onAction: (SettingsAction) -> Unit,
    onRestore: () -> Unit,
) {
    item(key = "fades") {
        Group(R.string.settings_tr_fades) {
            item { shapes ->
                FadeItem(
                    shapes,
                    stringResource(R.string.settings_tr_fade_in),
                    stringResource(R.string.settings_tr_fade_in_summary),
                    transitions.fadeInMs,
                ) { onAction(SettingsAction.SetTransitions(transitions.copy(fadeInMs = it))) }
            }
            item { shapes ->
                FadeItem(
                    shapes,
                    stringResource(R.string.settings_tr_fade_out),
                    stringResource(R.string.settings_tr_fade_out_summary),
                    transitions.fadeOutMs,
                ) { onAction(SettingsAction.SetTransitions(transitions.copy(fadeOutMs = it))) }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

/** Disabled (0) to 1000 ms in steps of 100, saved when the thumb is released. */
@Composable
private fun FadeItem(
    shapes: ListItemShapes,
    title: String,
    summary: String,
    ms: Int,
    onMs: (Int) -> Unit,
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = TransitionsPreferences.fadeMs(dragged?.roundToInt() ?: ms)
    val value =
        if (shown == 0) stringResource(R.string.settings_disabled) else stringResource(R.string.settings_ms, shown)
    SegmentedListItem(
        shapes = shapes,
        colors = settingsColors(),
        supportingContent = {
            Column {
                Text(summary)
                Text(value)
                Slider(
                    value = dragged ?: ms.toFloat(),
                    onValueChange = { dragged = it },
                    onValueChangeFinished = {
                        dragged?.let { onMs(TransitionsPreferences.fadeMs(it.roundToInt())) }
                        dragged = null
                    },
                    valueRange = 0f..TransitionsPreferences.MAX_FADE_MS.toFloat(),
                    steps = TransitionsPreferences.MAX_FADE_MS / TransitionsPreferences.FADE_STEP_MS - 1,
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            contentDescription = title
                            stateDescription = value
                        },
                )
            }
        },
    ) { Text(title) }
}
