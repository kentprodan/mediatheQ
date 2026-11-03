package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.kentprodan.mediatheq.core.data.CoverTap
import io.github.kentprodan.mediatheq.core.data.MiniProgress
import io.github.kentprodan.mediatheq.core.data.NowPlayingPreferences
import io.github.kentprodan.mediatheq.core.data.PlayerBackground
import io.github.kentprodan.mediatheq.core.data.PlayerProgress
import io.github.kentprodan.mediatheq.core.data.ProgressFallback
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** The dialogs of Settings -> Now playing screens. */
enum class NowPlayingDialog { MiniProgress, Background, Progress, Fallback, CoverTap, Restore }

/** Settings -> Interface -> Now playing screens (#126): the mini player and Now Playing; changes apply at once. */
internal fun LazyListScope.nowPlayingGroups(
    nowPlaying: NowPlayingPreferences,
    ratingMode: RatingMode,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (NowPlayingDialog) -> Unit,
) {
    val set = { changed: NowPlayingPreferences -> onAction(SettingsAction.SetNowPlaying(changed)) }
    item(key = "np_compact") {
        Group(R.string.settings_np_compact_group) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_np_floating),
                    stringResource(R.string.settings_np_floating_summary),
                    nowPlaying.floatingMiniPlayer,
                ) { set(nowPlaying.copy(floatingMiniPlayer = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_np_mini_swipe),
                    stringResource(R.string.settings_np_mini_swipe_summary),
                    nowPlaying.miniSwipeToSkip,
                ) { set(nowPlaying.copy(miniSwipeToSkip = it)) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_np_mini_progress),
                    stringResource(nowPlaying.miniProgress.label),
                ) { onOpenDialog(NowPlayingDialog.MiniProgress) }
            }
        }
    }
    item(key = "np_expanded") {
        Group(R.string.settings_np_expanded_group) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_np_background),
                    stringResource(nowPlaying.background.label),
                ) { onOpenDialog(NowPlayingDialog.Background) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_np_progress),
                    stringResource(nowPlaying.progress.label),
                ) { onOpenDialog(NowPlayingDialog.Progress) }
            }
            item { shapes ->
                // A plain bar as the main style never shows a waveform, so it needs no fallback.
                val usesWaveform = nowPlaying.progress.usesWaveform
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_np_fallback),
                    if (usesWaveform) {
                        stringResource(nowPlaying.fallback.label)
                    } else {
                        stringResource(R.string.settings_np_fallback_unused)
                    },
                    enabled = usesWaveform,
                ) { onOpenDialog(NowPlayingDialog.Fallback) }
            }
            item { shapes ->
                val ratingsOn = ratingMode != RatingMode.Off
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_np_rating),
                    stringResource(
                        if (ratingsOn) R.string.settings_np_rating_summary else R.string.settings_np_rating_off,
                    ),
                    nowPlaying.showRating && ratingsOn,
                    enabled = ratingsOn,
                ) { set(nowPlaying.copy(showRating = it)) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_np_cover_tap),
                    stringResource(nowPlaying.coverTap.label),
                ) { onOpenDialog(NowPlayingDialog.CoverTap) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_np_cover_swipe),
                    stringResource(R.string.settings_np_cover_swipe_summary),
                    nowPlaying.coverSwipe,
                ) { set(nowPlaying.copy(coverSwipe = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_np_play_button),
                    stringResource(R.string.settings_np_play_button_summary),
                    nowPlaying.playButtonWhenPaused,
                ) { set(nowPlaying.copy(playButtonWhenPaused = it)) }
            }
        }
    }
    restoreDefaultsGroup { onOpenDialog(NowPlayingDialog.Restore) }
}

@Composable
internal fun NowPlayingDialogHost(
    dialog: NowPlayingDialog,
    nowPlaying: NowPlayingPreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    if (dialog == NowPlayingDialog.Restore) {
        RestoreDefaultsDialog({ onAction(SettingsAction.RestoreNowPlayingDefaults) }, onDismiss)
        return
    }
    MqDialog(
        title = stringResource(dialog.title),
        confirmLabel = null,
        onConfirm = {},
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
    ) { NowPlayingChoices(dialog, nowPlaying, onAction, onDismiss) }
}

/** A dialog's content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
internal fun NowPlayingDialogContent(
    dialog: NowPlayingDialog,
    nowPlaying: NowPlayingPreferences,
) {
    MqDialogContent(stringResource(dialog.title), null, {}, stringResource(R.string.settings_cancel), {}) {
        NowPlayingChoices(dialog, nowPlaying, {}, {})
    }
}

private val NowPlayingDialog.title: Int
    get() =
        when (this) {
            NowPlayingDialog.MiniProgress -> R.string.settings_np_mini_progress
            NowPlayingDialog.Background -> R.string.settings_np_background
            NowPlayingDialog.Progress -> R.string.settings_np_progress
            NowPlayingDialog.Fallback -> R.string.settings_np_fallback
            NowPlayingDialog.CoverTap -> R.string.settings_np_cover_tap
            NowPlayingDialog.Restore -> R.string.settings_restore
        }

/** One row per option, a check on the selected one; a tap applies it and closes the dialog. */
@Composable
private fun NowPlayingChoices(
    dialog: NowPlayingDialog,
    nowPlaying: NowPlayingPreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows: List<Pair<Int, NowPlayingPreferences>> =
        when (dialog) {
            NowPlayingDialog.MiniProgress -> MiniProgress.entries.map { it.label to nowPlaying.copy(miniProgress = it) }
            NowPlayingDialog.Background -> PlayerBackground.entries.map { it.label to nowPlaying.copy(background = it) }
            NowPlayingDialog.Progress -> PlayerProgress.entries.map { it.label to nowPlaying.copy(progress = it) }
            NowPlayingDialog.Fallback -> ProgressFallback.entries.map { it.label to nowPlaying.copy(fallback = it) }
            NowPlayingDialog.CoverTap -> CoverTap.entries.map { it.label to nowPlaying.copy(coverTap = it) }
            NowPlayingDialog.Restore -> emptyList()
        }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        rows.forEach { (label, choice) ->
            val selected = choice == nowPlaying
            ListItem(
                headlineContent = { Text(stringResource(label)) },
                trailingContent = if (selected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier =
                    Modifier.selectable(selected, role = Role.RadioButton) {
                        onAction(SettingsAction.SetNowPlaying(choice))
                        onDismiss()
                    },
            )
        }
    }
}

private val PlayerProgress.usesWaveform: Boolean
    get() = this != PlayerProgress.Basic && this != PlayerProgress.Wavy

private val MiniProgress.label: Int
    get() =
        when (this) {
            MiniProgress.Hidden -> R.string.settings_np_mini_hidden
            MiniProgress.Line -> R.string.settings_np_mini_line
            MiniProgress.Background -> R.string.settings_np_mini_background
        }

private val PlayerBackground.label: Int
    get() =
        when (this) {
            PlayerBackground.Solid -> R.string.settings_np_background_solid
            PlayerBackground.Gradient -> R.string.settings_np_background_gradient
            PlayerBackground.Theme -> R.string.settings_np_background_theme
        }

private val PlayerProgress.label: Int
    get() =
        when (this) {
            PlayerProgress.WaveformSharp -> R.string.settings_np_progress_sharp
            PlayerProgress.WaveformSmooth -> R.string.settings_np_progress_smooth
            PlayerProgress.WaveformBars -> R.string.settings_np_progress_bars
            PlayerProgress.WaveformBarsReflected -> R.string.settings_np_progress_bars_reflected
            PlayerProgress.Basic -> R.string.settings_np_basic
            PlayerProgress.Wavy -> R.string.settings_np_wavy
        }

private val ProgressFallback.label: Int
    get() =
        when (this) {
            ProgressFallback.Basic -> R.string.settings_np_basic
            ProgressFallback.Wavy -> R.string.settings_np_wavy
        }

private val CoverTap.label: Int
    get() =
        when (this) {
            CoverTap.PlayPause -> R.string.settings_np_cover_tap_play_pause
            CoverTap.ShowLyrics -> R.string.settings_np_cover_tap_lyrics
            CoverTap.None -> R.string.settings_np_cover_tap_none
        }
