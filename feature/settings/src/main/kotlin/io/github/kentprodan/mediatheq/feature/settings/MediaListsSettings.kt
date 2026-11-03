package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.SwipeAction
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.TopButtonStyle
import io.github.kentprodan.mediatheq.core.data.TrackInfo
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** The dialogs of Settings -> Media lists. */
enum class MediaListsDialog {
    TopButtonStyle,
    HiddenTopButtons,
    TrackInfo,
    SecondTrackInfo,
    SwipeRight,
    SwipeLeft,
    Restore,
}

/** Settings -> Interface -> Media lists (#123): every change applies at once in every list. */
internal fun LazyListScope.mediaListsGroups(
    lists: MediaListPreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (MediaListsDialog) -> Unit,
) {
    val set = { changed: MediaListPreferences -> onAction(SettingsAction.SetMediaLists(changed)) }
    item(key = "list_header") {
        Group(R.string.settings_ml_header_group) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ml_item_counts),
                    stringResource(R.string.settings_ml_item_counts_summary),
                    lists.showItemCounts,
                ) { set(lists.copy(showItemCounts = it)) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_ml_top_button_style),
                    stringResource(lists.topButtonStyle.label),
                ) { onOpenDialog(MediaListsDialog.TopButtonStyle) }
            }
            item { shapes ->
                val hidden = TopButton.entries.filter { it in lists.hiddenTopButtons }.map { stringResource(it.label) }
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_ml_hidden_top_buttons),
                    hidden.joinToString(", ").ifEmpty { stringResource(R.string.settings_ml_none) },
                ) { onOpenDialog(MediaListsDialog.HiddenTopButtons) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ml_inverted_sort_arrows),
                    stringResource(R.string.settings_ml_inverted_sort_arrows_summary),
                    lists.invertedSortArrows,
                ) { set(lists.copy(invertedSortArrows = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ml_hide_back),
                    stringResource(R.string.settings_ml_hide_back_summary),
                    lists.hideBackButton,
                ) { set(lists.copy(hideBackButton = it)) }
            }
        }
    }
    item(key = "list_tracks") {
        Group(R.string.settings_ml_tracks_group) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_ml_track_info),
                    stringResource(lists.trackInfo.label),
                ) { onOpenDialog(MediaListsDialog.TrackInfo) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_ml_second_track_info),
                    stringResource(lists.secondTrackInfo.label),
                ) { onOpenDialog(MediaListsDialog.SecondTrackInfo) }
            }
        }
    }
    item(key = "list_covers") {
        Group(R.string.settings_ml_covers_group) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ml_center_grid),
                    stringResource(R.string.settings_ml_center_grid_summary),
                    lists.centerGridText,
                ) { set(lists.copy(centerGridText = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ml_favorite_overlay),
                    stringResource(R.string.settings_ml_favorite_overlay_summary),
                    lists.favoriteOverlay,
                ) { set(lists.copy(favoriteOverlay = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ml_offline_overlay),
                    stringResource(R.string.settings_ml_offline_overlay_summary),
                    lists.offlineOverlay,
                ) { set(lists.copy(offlineOverlay = it)) }
            }
        }
    }
    item(key = "list_swipes") {
        Group(R.string.settings_ml_swipe_group) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_ml_swipe_right),
                    stringResource(lists.swipeRight.label),
                ) { onOpenDialog(MediaListsDialog.SwipeRight) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_ml_swipe_left),
                    stringResource(lists.swipeLeft.label),
                ) { onOpenDialog(MediaListsDialog.SwipeLeft) }
            }
        }
    }
    restoreDefaultsGroup { onOpenDialog(MediaListsDialog.Restore) }
}

@Composable
internal fun MediaListsDialogHost(
    dialog: MediaListsDialog,
    lists: MediaListPreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        MediaListsDialog.Restore -> {
            RestoreDefaultsDialog({ onAction(SettingsAction.RestoreMediaListsDefaults) }, onDismiss)
        }

        MediaListsDialog.HiddenTopButtons -> {
            // The checks live here until OK; Cancel drops them.
            var hidden by rememberSaveable { mutableStateOf(lists.hiddenTopButtons.toList()) }
            MqDialog(
                title = stringResource(dialog.title),
                confirmLabel = stringResource(R.string.settings_ok),
                onConfirm = {
                    onAction(SettingsAction.SetMediaLists(lists.copy(hiddenTopButtons = hidden.toSet())))
                    onDismiss()
                },
                dismissLabel = stringResource(R.string.settings_cancel),
                onDismiss = onDismiss,
            ) { HiddenTopButtons(hidden.toSet()) { hidden = it.toList() } }
        }

        else -> {
            MqDialog(
                title = stringResource(dialog.title),
                confirmLabel = null,
                onConfirm = {},
                dismissLabel = stringResource(R.string.settings_cancel),
                onDismiss = onDismiss,
            ) { MediaListsChoices(dialog, lists, onAction, onDismiss) }
        }
    }
}

/** A dialog's content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
internal fun MediaListsDialogContent(
    dialog: MediaListsDialog,
    lists: MediaListPreferences,
) {
    val ok = stringResource(R.string.settings_ok).takeIf { dialog == MediaListsDialog.HiddenTopButtons }
    MqDialogContent(stringResource(dialog.title), ok, {}, stringResource(R.string.settings_cancel), {}) {
        if (dialog == MediaListsDialog.HiddenTopButtons) {
            HiddenTopButtons(lists.hiddenTopButtons) {}
        } else {
            MediaListsChoices(dialog, lists, {}, {})
        }
    }
}

private val MediaListsDialog.title: Int
    get() =
        when (this) {
            MediaListsDialog.TopButtonStyle -> R.string.settings_ml_top_button_style
            MediaListsDialog.HiddenTopButtons -> R.string.settings_ml_hidden_top_buttons
            MediaListsDialog.TrackInfo -> R.string.settings_ml_track_info
            MediaListsDialog.SecondTrackInfo -> R.string.settings_ml_second_track_info
            MediaListsDialog.SwipeRight -> R.string.settings_ml_swipe_right
            MediaListsDialog.SwipeLeft -> R.string.settings_ml_swipe_left
            MediaListsDialog.Restore -> R.string.settings_restore
        }

/** One row per option, a check on the selected one; a tap applies it and closes the dialog. */
@Composable
private fun MediaListsChoices(
    dialog: MediaListsDialog,
    lists: MediaListPreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows: List<Pair<Int, MediaListPreferences>> =
        when (dialog) {
            MediaListsDialog.TopButtonStyle -> {
                TopButtonStyle.entries.map {
                    it.label to
                        lists.copy(
                            topButtonStyle = it,
                        )
                }
            }

            MediaListsDialog.TrackInfo -> {
                TrackInfo.entries.map { it.label to lists.copy(trackInfo = it) }
            }

            MediaListsDialog.SecondTrackInfo -> {
                TrackInfo.entries.map { it.label to lists.copy(secondTrackInfo = it) }
            }

            MediaListsDialog.SwipeRight -> {
                SwipeAction.entries.map { it.label to lists.copy(swipeRight = it) }
            }

            MediaListsDialog.SwipeLeft -> {
                SwipeAction.entries.map { it.label to lists.copy(swipeLeft = it) }
            }

            MediaListsDialog.HiddenTopButtons, MediaListsDialog.Restore -> {
                emptyList()
            }
        }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        rows.forEach { (label, choice) ->
            val selected = choice == lists
            ListItem(
                headlineContent = { Text(stringResource(label)) },
                trailingContent = if (selected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier =
                    Modifier.selectable(selected, role = Role.RadioButton) {
                        onAction(SettingsAction.SetMediaLists(choice))
                        onDismiss()
                    },
            )
        }
    }
}

@Composable
private fun HiddenTopButtons(
    hidden: Set<TopButton>,
    onChange: (Set<TopButton>) -> Unit,
) {
    Column {
        TopButton.entries.forEach { button ->
            val checked = button in hidden
            ListItem(
                headlineContent = { Text(stringResource(button.label)) },
                leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier =
                    Modifier.toggleable(checked, role = Role.Checkbox) {
                        onChange(if (checked) hidden - button else hidden + button)
                    },
            )
        }
    }
}

private val TopButtonStyle.label: Int
    get() =
        when (this) {
            TopButtonStyle.Hidden -> R.string.settings_ml_style_hidden
            TopButtonStyle.Normal -> R.string.settings_ml_style_normal
            TopButtonStyle.SmallIcons -> R.string.settings_ml_style_small
            TopButtonStyle.LargeIcons -> R.string.settings_ml_style_large
        }

private val TopButton.label: Int
    get() =
        when (this) {
            TopButton.Play -> R.string.settings_track_play
            TopButton.Shuffle -> R.string.settings_ml_shuffle
            TopButton.PlayNext -> R.string.settings_track_play_next
            TopButton.AddToQueue -> R.string.settings_track_add_to_queue
        }

private val TrackInfo.label: Int
    get() =
        when (this) {
            TrackInfo.None -> R.string.settings_ml_none
            TrackInfo.Duration -> R.string.settings_ml_info_duration
            TrackInfo.Favorite -> R.string.settings_ml_info_favorite
            TrackInfo.Rating -> R.string.settings_ml_info_rating
            TrackInfo.PlayCount -> R.string.settings_ml_info_play_count
            TrackInfo.Offline -> R.string.settings_ml_info_offline
            TrackInfo.Codec -> R.string.settings_ml_info_codec
            TrackInfo.Bitrate -> R.string.settings_ml_info_bitrate
            TrackInfo.CodecAndBitrate -> R.string.settings_ml_info_codec_bitrate
        }

private val SwipeAction.label: Int
    get() =
        when (this) {
            SwipeAction.Disabled -> R.string.settings_ml_swipe_disabled
            SwipeAction.AddToQueue -> R.string.settings_track_add_to_queue
            SwipeAction.PlayNext -> R.string.settings_track_play_next
            SwipeAction.AddToPlaylist -> R.string.settings_ml_swipe_add_to_playlist
            SwipeAction.Download -> R.string.settings_ml_swipe_download
            SwipeAction.ToggleFavorite -> R.string.settings_ml_swipe_favorite
            SwipeAction.Rate -> R.string.settings_ml_swipe_rate
        }
