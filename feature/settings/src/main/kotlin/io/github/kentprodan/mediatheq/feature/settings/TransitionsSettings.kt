package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.kentprodan.mediatheq.core.data.FadeCurve
import io.github.kentprodan.mediatheq.core.data.TransitionsPreferences
import kotlin.math.roundToInt

/** Settings -> Playback -> Transitions (#142): fade in and fade out, and crossfade (#219). */
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
    item(key = "crossfade") {
        val on = transitions.crossfadeMs > 0

        fun set(changed: TransitionsPreferences) = onAction(SettingsAction.SetTransitions(changed))
        Group(R.string.settings_tr_crossfade_group) {
            item { shapes ->
                LengthItem(
                    shapes,
                    stringResource(R.string.settings_tr_crossfade),
                    stringResource(R.string.settings_tr_crossfade_summary),
                    transitions.crossfadeMs,
                    max = TransitionsPreferences.MAX_CROSSFADE_MS,
                    step = TransitionsPreferences.CROSSFADE_STEP_MS,
                    round = TransitionsPreferences::crossfadeMs,
                    label = {
                        stringResource(
                            R.string.settings_seconds,
                            it / TransitionsPreferences.CROSSFADE_STEP_MS,
                        )
                    },
                ) { set(transitions.copy(crossfadeMs = it)) }
            }
            // The rest only matters with a length: hidden while crossfade is Disabled.
            if (on) {
                item { shapes ->
                    SwitchItem(
                        shapes,
                        stringResource(R.string.settings_tr_smart),
                        stringResource(R.string.settings_tr_smart_summary),
                        transitions.smartFades,
                    ) { set(transitions.copy(smartFades = it)) }
                }
                item { shapes ->
                    ChoiceItem(
                        shapes,
                        stringResource(R.string.settings_tr_out_curve),
                        null,
                        FadeCurve.entries,
                        transitions.fadeOutCurve,
                        { stringResource(it.label) },
                        { set(transitions.copy(fadeOutCurve = it)) },
                    )
                }
                item { shapes ->
                    ChoiceItem(
                        shapes,
                        stringResource(R.string.settings_tr_in_curve),
                        null,
                        FadeCurve.entries,
                        transitions.fadeInCurve,
                        { stringResource(it.label) },
                        { set(transitions.copy(fadeInCurve = it)) },
                    )
                }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

private val FadeCurve.label: Int
    get() =
        when (this) {
            FadeCurve.Linear -> R.string.settings_curve_linear
            FadeCurve.EqualPower -> R.string.settings_curve_equal_power
            FadeCurve.Exponential -> R.string.settings_curve_exponential
        }

/** A fade of [ms]: disabled (0) to 1000 ms in steps of 100, saved when the thumb is released. */
@Composable
private fun FadeItem(
    shapes: ListItemShapes,
    title: String,
    summary: String,
    ms: Int,
    onMs: (Int) -> Unit,
) = LengthItem(
    shapes,
    title,
    summary,
    ms,
    max = TransitionsPreferences.MAX_FADE_MS,
    step = TransitionsPreferences.FADE_STEP_MS,
    round = TransitionsPreferences::fadeMs,
    label = { stringResource(R.string.settings_ms, it) },
    onMs = onMs,
)

/** A length of [ms] from disabled (0) to [max] in [step]s, saved when the thumb is released. */
@Composable
private fun LengthItem(
    shapes: ListItemShapes,
    title: String,
    summary: String,
    ms: Int,
    max: Int,
    step: Int,
    round: (Int) -> Int,
    label: @Composable (Int) -> String,
    onMs: (Int) -> Unit,
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    // The thumb stays where it was released until the saved value arrives.
    LaunchedEffect(ms) { dragged = null }
    val shown = round(dragged?.roundToInt() ?: ms)
    val value = if (shown == 0) stringResource(R.string.settings_disabled) else label(shown)
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
                    onValueChangeFinished = { dragged?.let { onMs(round(it.roundToInt())) } },
                    valueRange = 0f..max.toFloat(),
                    steps = max / step - 1,
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
