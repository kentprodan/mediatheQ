package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.data.PlaybackBehavior
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/**
 * Settings -> Playback (#138): a hub of sub-pages. An entry comes with its page (#120): Output settings, Automatic
 * actions, Transitions, Audio focus, Headset and Notification buttons are added by their own issues. Normalization
 * and the Audio page (bit-perfect USB, equalizer) stay here until Output settings takes them.
 */
internal fun LazyListScope.playbackHubGroups(
    audio: AudioPreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenBehavior: () -> Unit,
    onOpenDecoding: () -> Unit,
    onOpenAudio: () -> Unit,
) {
    item(key = "playback_hub") {
        Group(R.string.settings_playback) {
            item { shapes ->
                HubRow(
                    shapes,
                    R.string.settings_pb_behavior,
                    R.string.settings_pb_behavior_summary,
                    MqIcons.PlayArrow,
                ) {
                    onOpenBehavior()
                }
            }
            item { shapes ->
                HubRow(shapes, R.string.settings_decoding, R.string.settings_decoding_summary, MqIcons.GraphicEq) {
                    onOpenDecoding()
                }
            }
        }
    }
    item(key = "playback_audio") {
        Group(R.string.settings_audio) {
            item { shapes ->
                ChoiceItem(
                    shapes,
                    stringResource(R.string.settings_normalization),
                    stringResource(R.string.settings_normalization_summary),
                    Normalization.entries,
                    selected = audio.normalization,
                    label = { stringResource(it.label) },
                    onSelect = { onAction(SettingsAction.SetNormalization(it)) },
                )
            }
            item { shapes ->
                HubRow(shapes, R.string.settings_audio, R.string.settings_audio_summary, MqIcons.Tune, onOpenAudio)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HubRow(
    shapes: androidx.compose.material3.ListItemShapes,
    title: Int,
    summary: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = settingsColors(),
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = { Text(stringResource(summary)) },
    ) { Text(stringResource(title)) }
}

/** Settings -> Playback -> Decoding and transcoding (#138). */
internal fun LazyListScope.decodingGroups(
    audio: AudioPreferences,
    onAction: (SettingsAction) -> Unit,
    onRestore: () -> Unit,
) {
    item(key = "decoding") {
        Group(R.string.settings_decoding_streaming) {
            listOf(true to R.string.settings_quality_metered, false to R.string.settings_quality_unmetered)
                .forEach { (metered, title) ->
                    item { shapes ->
                        ChoiceItem(
                            shapes,
                            stringResource(title),
                            stringResource(R.string.settings_quality_summary),
                            StreamingQuality.entries,
                            selected = audio.qualityFor(metered),
                            label = { it.label() },
                            onSelect = { onAction(SettingsAction.SetStreamingQuality(metered, it)) },
                        )
                    }
                }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_force_instant_transcoding),
                    stringResource(R.string.settings_force_instant_transcoding_summary),
                    audio.forceInstantTranscoding,
                ) { onAction(SettingsAction.SetForceInstantTranscoding(it)) }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

/** Settings -> Playback -> Playback (#138): how the queue and the transport buttons behave. */
internal fun LazyListScope.behaviorGroups(
    behavior: PlaybackBehavior,
    onAction: (SettingsAction) -> Unit,
    onRestore: () -> Unit,
) {
    val set = { it: PlaybackBehavior -> onAction(SettingsAction.SetBehavior(it)) }
    val rows: List<Triple<Int, Int, Pair<Boolean, (Boolean) -> PlaybackBehavior>>> =
        listOf(
            Triple(
                R.string.settings_pb_preserve_play_next,
                R.string.settings_pb_preserve_play_next_summary,
                behavior.preservePlayNextOrder to { on -> behavior.copy(preservePlayNextOrder = on) },
            ),
            Triple(
                R.string.settings_pb_rewind_first,
                R.string.settings_pb_rewind_first_summary,
                behavior.rewindFirst to { on -> behavior.copy(rewindFirst = on) },
            ),
            Triple(
                R.string.settings_pb_circular,
                R.string.settings_pb_circular_summary,
                behavior.circular to { on -> behavior.copy(circular = on) },
            ),
            Triple(
                R.string.settings_pb_keep_paused,
                R.string.settings_pb_keep_paused_summary,
                behavior.keepPausedOnSkip to { on -> behavior.copy(keepPausedOnSkip = on) },
            ),
            Triple(
                R.string.settings_pb_stop_repeat_one,
                R.string.settings_pb_stop_repeat_one_summary,
                behavior.stopRepeatOneOnSkip to { on -> behavior.copy(stopRepeatOneOnSkip = on) },
            ),
            Triple(
                R.string.settings_pb_retain_state,
                R.string.settings_pb_retain_state_summary,
                behavior.retainPlayerState to { on -> behavior.copy(retainPlayerState = on) },
            ),
            Triple(
                R.string.settings_pb_weighted_shuffle,
                R.string.settings_pb_weighted_shuffle_summary,
                behavior.weightedShuffle to { on -> behavior.copy(weightedShuffle = on) },
            ),
        )
    item(key = "behavior") {
        Group(R.string.settings_pb_queue) {
            rows.forEach { (title, summary, value) ->
                val (checked, change) = value
                item { shapes ->
                    SwitchItem(shapes, stringResource(title), stringResource(summary), checked) { set(change(it)) }
                }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}
