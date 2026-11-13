package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.kentprodan.mediatheq.core.data.LandscapeNavStyle
import io.github.kentprodan.mediatheq.core.data.MainNavEntry
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.PortraitNavStyle
import io.github.kentprodan.mediatheq.core.data.TrackAction
import io.github.kentprodan.mediatheq.core.data.entriesFor
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.AppMode
import kotlin.math.roundToInt

/** The choice dialogs of Settings -> Navigation and media start (the entries dialog is ViewModel state). */
enum class NavigationDialog { PortraitStyle, LandscapeStyle, TrackAction, Restore }

/** Settings -> Interface -> Navigation and media start (#122): every change applies at once. */
internal fun LazyListScope.navigationGroups(
    navigation: NavigationPreferences,
    mode: AppMode,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (NavigationDialog) -> Unit,
) {
    val set = { changed: NavigationPreferences -> onAction(SettingsAction.SetNavigation(changed)) }
    item(key = "navigation") {
        Group(R.string.settings_navigation_group) {
            item { shapes ->
                val labels = navigation.entriesFor(mode).map { stringResource(it.label) }
                DialogItem(shapes, stringResource(R.string.settings_nav_entries), labels.joinToString(", ")) {
                    onAction(SettingsAction.OpenNavEntries)
                }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_nav_portrait),
                    stringResource(navigation.portraitStyle.label),
                ) { onOpenDialog(NavigationDialog.PortraitStyle) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_nav_landscape),
                    stringResource(navigation.landscapeStyle.label),
                ) { onOpenDialog(NavigationDialog.LandscapeStyle) }
            }
            item { shapes ->
                TransparencyItem(
                    shapes,
                    navigation.transparencyPercent,
                ) { set(navigation.copy(transparencyPercent = it)) }
            }
        }
    }
    item(key = "media_start") {
        Group(R.string.settings_media_start_group) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_slow_start),
                    stringResource(R.string.settings_slow_start_summary),
                    checked = navigation.slowStartMessages,
                    onCheckedChange = { set(navigation.copy(slowStartMessages = it)) },
                )
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_track_action),
                    stringResource(navigation.trackAction.label),
                ) { onOpenDialog(NavigationDialog.TrackAction) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_expand_player),
                    stringResource(R.string.settings_expand_player_summary),
                    checked = navigation.expandPlayer,
                    onCheckedChange = { set(navigation.copy(expandPlayer = it)) },
                )
            }
        }
    }
    restoreDefaultsGroup { onOpenDialog(NavigationDialog.Restore) }
}

/** 0-100 % in steps of 10, saved when the thumb is released. */
@Composable
private fun TransparencyItem(
    shapes: ListItemShapes,
    percent: Int,
    onPercent: (Int) -> Unit,
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = dragged?.roundToInt() ?: percent
    val title = stringResource(R.string.settings_nav_transparency)
    val value = stringResource(R.string.settings_percent, shown)
    SegmentedListItem(
        shapes = shapes,
        colors = settingsColors(),
        supportingContent = {
            Column {
                Text(value)
                Slider(
                    value = dragged ?: percent.toFloat(),
                    onValueChange = { dragged = it },
                    onValueChangeFinished = {
                        dragged?.let { onPercent(it.roundToInt()) }
                        dragged = null
                    },
                    valueRange = 0f..100f,
                    steps = 100 / NavigationPreferences.TRANSPARENCY_STEP - 1,
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

@Composable
internal fun NavigationDialogHost(
    dialog: NavigationDialog,
    navigation: NavigationPreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    if (dialog == NavigationDialog.Restore) {
        RestoreDefaultsDialog({ onAction(SettingsAction.RestoreNavigationDefaults) }, onDismiss)
    } else {
        MqDialog(
            title = stringResource(dialog.title),
            confirmLabel = null,
            onConfirm = {},
            dismissLabel = stringResource(R.string.settings_cancel),
            onDismiss = onDismiss,
        ) { NavigationChoices(dialog, navigation, onAction, onDismiss) }
    }
}

/** A choice dialog's content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
internal fun NavigationChoiceDialogContent(
    dialog: NavigationDialog,
    navigation: NavigationPreferences,
) {
    MqDialogContent(stringResource(dialog.title), null, {}, stringResource(R.string.settings_cancel), {}) {
        NavigationChoices(dialog, navigation, {}, {})
    }
}

private val NavigationDialog.title: Int
    get() =
        when (this) {
            NavigationDialog.PortraitStyle -> R.string.settings_nav_portrait
            NavigationDialog.LandscapeStyle -> R.string.settings_nav_landscape
            NavigationDialog.TrackAction -> R.string.settings_track_action
            NavigationDialog.Restore -> R.string.settings_restore
        }

/** One row per option, a check on the selected one; a tap applies it and closes the dialog. */
@Composable
private fun NavigationChoices(
    dialog: NavigationDialog,
    navigation: NavigationPreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows: List<Pair<Int, NavigationPreferences>> =
        when (dialog) {
            NavigationDialog.PortraitStyle -> {
                PortraitNavStyle.entries.map {
                    it.label to
                        navigation.copy(portraitStyle = it)
                }
            }

            NavigationDialog.LandscapeStyle -> {
                LandscapeNavStyle.entries.map {
                    it.label to
                        navigation.copy(landscapeStyle = it)
                }
            }

            NavigationDialog.TrackAction -> {
                TrackAction.entries.map { it.label to navigation.copy(trackAction = it) }
            }

            NavigationDialog.Restore -> {
                emptyList()
            }
        }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        rows.forEach { (label, choice) ->
            val selected = choice == navigation
            ListItem(
                headlineContent = { Text(stringResource(label)) },
                trailingContent = if (selected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier =
                    Modifier.selectable(selected, role = Role.RadioButton) {
                        onAction(SettingsAction.SetNavigation(choice))
                        onDismiss()
                    },
            )
        }
    }
}

/** "Organize your navigation": check up to six entries (Home always) and drag them into order; OK applies. */
@Composable
internal fun NavEntriesDialog(
    draft: NavEntriesDraft,
    onAction: (SettingsAction) -> Unit,
) {
    OrganizeDialog(
        stringResource(R.string.settings_nav_entries_dialog),
        draft,
        label = { stringResource(it.label) },
        onToggle = { onAction(SettingsAction.ToggleNavEntry(it)) },
        onMove = { from, to -> onAction(SettingsAction.MoveNavEntry(from, to)) },
        onConfirm = { onAction(SettingsAction.ConfirmNavEntries) },
        onDismiss = { onAction(SettingsAction.DismissNavEntries) },
        summary = stringResource(R.string.settings_nav_entries_limit, NavigationPreferences.MAX_ENTRIES),
    )
}

/** The entries dialog's content on its own, for screenshot tests. */
@Composable
internal fun NavEntriesDialogContent(draft: NavEntriesDraft) {
    OrganizeDialogContent(
        stringResource(R.string.settings_nav_entries_dialog),
        draft,
        label = { stringResource(it.label) },
        summary = stringResource(R.string.settings_nav_entries_limit, NavigationPreferences.MAX_ENTRIES),
    )
}

@Composable
internal fun NavEntriesList(
    draft: NavEntriesDraft,
    onAction: (SettingsAction) -> Unit,
) {
    OrganizeList(
        draft,
        label = { stringResource(it.label) },
        onToggle = { onAction(SettingsAction.ToggleNavEntry(it)) },
        onMove = { from, to -> onAction(SettingsAction.MoveNavEntry(from, to)) },
        summary = stringResource(R.string.settings_nav_entries_limit, NavigationPreferences.MAX_ENTRIES),
    )
}

internal val MainNavEntry.label: Int
    get() =
        when (this) {
            MainNavEntry.Home -> R.string.settings_entry_home
            MainNavEntry.Library -> R.string.settings_entry_library
            MainNavEntry.Albums -> R.string.settings_entry_albums
            MainNavEntry.AlbumArtists -> R.string.settings_entry_album_artists
            MainNavEntry.Genres -> R.string.settings_entry_genres
            MainNavEntry.RecordLabels -> R.string.settings_entry_record_labels
            MainNavEntry.Playlists -> R.string.settings_entry_playlists
            MainNavEntry.Tracks -> R.string.settings_entry_tracks
            MainNavEntry.Search -> R.string.settings_entry_search
            MainNavEntry.Downloads -> R.string.settings_entry_downloads
        }

private val PortraitNavStyle.label: Int
    get() =
        when (this) {
            PortraitNavStyle.BottomBar -> R.string.settings_nav_bottom_bar
            PortraitNavStyle.FloatingToolbar -> R.string.settings_nav_floating_toolbar
            PortraitNavStyle.FloatingToolbarWithFab -> R.string.settings_nav_floating_toolbar_fab
        }

private val LandscapeNavStyle.label: Int
    get() =
        when (this) {
            LandscapeNavStyle.NavigationRail -> R.string.settings_nav_rail
            LandscapeNavStyle.SameAsPortrait -> R.string.settings_nav_same_as_portrait
        }

internal val TrackAction.label: Int
    get() =
        when (this) {
            TrackAction.Play -> R.string.settings_track_play
            TrackAction.PlayOnlyThis -> R.string.settings_track_play_only
            TrackAction.PlayNext -> R.string.settings_track_play_next
            TrackAction.AddToQueue -> R.string.settings_track_add_to_queue
        }
