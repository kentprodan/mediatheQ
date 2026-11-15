package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.BehaviorSwitch
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.data.PlaybackBehavior
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/**
 * Settings -> Playback (#138): a hub of sub-pages. An entry comes with its page (#120). Normalization and the
 * equalizer live in Output settings, per output (#140).
 */
internal fun LazyListScope.playbackHubGroups(pages: PlaybackPages) {
    item(key = "playback_hub") {
        Group(R.string.settings_playback) {
            item { shapes ->
                HubRow(
                    shapes,
                    R.string.settings_pb_behavior,
                    R.string.settings_pb_behavior_summary,
                    MqIcons.PlayArrow,
                    pages.behavior,
                )
            }
            if (pages.showVideo) {
                item { shapes ->
                    HubRow(shapes, R.string.settings_video, R.string.settings_video_summary, MqIcons.Movie, pages.video)
                }
            }
            if (pages.music) {
                item { shapes ->
                    HubRow(
                        shapes,
                        R.string.settings_decoding,
                        R.string.settings_decoding_summary,
                        MqIcons.GraphicEq,
                        pages.decoding,
                    )
                }
            }
            item { shapes ->
                HubRow(
                    shapes,
                    R.string.settings_outputs,
                    R.string.settings_outputs_summary,
                    MqIcons.Tune,
                    pages.outputs,
                )
            }
            item { shapes ->
                HubRow(
                    shapes,
                    R.string.settings_audio_focus,
                    R.string.settings_audio_focus_summary,
                    MqIcons.VolumeDown,
                    pages.audioFocus,
                )
            }
            item { shapes ->
                HubRow(
                    shapes,
                    R.string.settings_headset,
                    R.string.settings_headset_summary,
                    MqIcons.Headphones,
                    pages.headset,
                )
            }
            item { shapes ->
                HubRow(
                    shapes,
                    R.string.settings_notification_buttons,
                    R.string.settings_notification_buttons_summary,
                    MqIcons.Notifications,
                    pages.notificationButtons,
                )
            }
            if (pages.music) {
                item { shapes ->
                    HubRow(
                        shapes,
                        R.string.settings_transitions,
                        R.string.settings_transitions_summary,
                        MqIcons.Sort,
                        pages.transitions,
                    )
                }
            }
            item { shapes ->
                HubRow(
                    shapes,
                    R.string.settings_auto_actions,
                    R.string.settings_auto_actions_summary,
                    MqIcons.Devices,
                    pages.autoActions,
                )
            }
        }
    }
}

/** Where the Playback hub's rows lead. */
internal class PlaybackPages(
    val behavior: () -> Unit,
    val decoding: () -> Unit,
    val audioFocus: () -> Unit,
    val headset: () -> Unit,
    val notificationButtons: () -> Unit,
    val outputs: () -> Unit,
    val autoActions: () -> Unit,
    val transitions: () -> Unit,
    /** Decoding and transcoding and Transitions are music only (ADR 0051). */
    val music: Boolean = true,
    /** Settings -> Playback -> Video (#276), shown when the app mode has video. */
    val video: () -> Unit = {},
    val showVideo: Boolean = false,
)

@androidx.compose.runtime.Composable
internal fun HubRow(
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

/** Settings -> Playback -> Playback (#138): how the queue and the transport buttons behave, and Bluetooth lyrics (#143). */
internal fun LazyListScope.behaviorGroups(
    behavior: PlaybackBehavior,
    music: Boolean,
    onAction: (SettingsAction) -> Unit,
    onRestore: () -> Unit,
) {
    item(key = "behavior") {
        Group(R.string.settings_pb_queue) {
            // Bluetooth lyrics are music only (ADR 0051).
            BehaviorSwitch.entries.filter { music || it != BehaviorSwitch.BluetoothLyrics }.forEach { switch ->
                val (title, summary) = switch.texts
                item { shapes ->
                    SwitchItem(shapes, stringResource(title), stringResource(summary), behavior.isOn(switch)) {
                        onAction(SettingsAction.SetBehavior(switch, it))
                    }
                }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

/** Title and summary of each switch, in the page's order. */
private val BehaviorSwitch.texts: Pair<Int, Int>
    get() =
        when (this) {
            BehaviorSwitch.PreservePlayNextOrder -> {
                R.string.settings_pb_preserve_play_next to R.string.settings_pb_preserve_play_next_summary
            }

            BehaviorSwitch.RewindFirst -> {
                R.string.settings_pb_rewind_first to R.string.settings_pb_rewind_first_summary
            }

            BehaviorSwitch.Circular -> {
                R.string.settings_pb_circular to R.string.settings_pb_circular_summary
            }

            BehaviorSwitch.KeepPausedOnSkip -> {
                R.string.settings_pb_keep_paused to R.string.settings_pb_keep_paused_summary
            }

            BehaviorSwitch.StopRepeatOneOnSkip -> {
                R.string.settings_pb_stop_repeat_one to R.string.settings_pb_stop_repeat_one_summary
            }

            BehaviorSwitch.RetainPlayerState -> {
                R.string.settings_pb_retain_state to R.string.settings_pb_retain_state_summary
            }

            BehaviorSwitch.WeightedShuffle -> {
                R.string.settings_pb_weighted_shuffle to R.string.settings_pb_weighted_shuffle_summary
            }

            BehaviorSwitch.BluetoothLyrics -> {
                R.string.settings_pb_bluetooth_lyrics to R.string.settings_pb_bluetooth_lyrics_summary
            }
        }
