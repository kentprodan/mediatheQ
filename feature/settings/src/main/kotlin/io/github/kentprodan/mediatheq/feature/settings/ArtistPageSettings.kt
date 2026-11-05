package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import io.github.kentprodan.mediatheq.core.data.ArtistButton
import io.github.kentprodan.mediatheq.core.data.ArtistHeaderBackground
import io.github.kentprodan.mediatheq.core.data.ArtistPagePreferences
import io.github.kentprodan.mediatheq.core.data.ArtistPlayAction
import io.github.kentprodan.mediatheq.core.data.ArtistSection
import io.github.kentprodan.mediatheq.core.data.MoreAlbumsSort
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** The dialogs of Settings -> Artist page (the section order is ViewModel state, like the Album page's). */
enum class ArtistPageDialog {
    Background,
    HiddenButtons,
    PlayAction,
    AlbumsSort,
    TopTracksCount,
    DynamicColors,
    Restore,
}

/** Settings -> Interface -> Page settings -> Artist page (#133): every change applies at once. */
internal fun LazyListScope.artistPageGroups(
    page: ArtistPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (ArtistPageDialog) -> Unit,
) {
    val set = { changed: ArtistPagePreferences -> onAction(SettingsAction.SetArtistPage(changed)) }
    item(key = "arp_header") {
        Group(R.string.settings_arp_header) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_arp_background),
                    stringResource(page.headerBackground.label),
                ) { onOpenDialog(ArtistPageDialog.Background) }
            }
            item { shapes ->
                val hidden = ArtistButton.entries.filter { it in page.hiddenButtons }.map { stringResource(it.label) }
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_arp_hidden_buttons),
                    hidden.joinToString(", ").ifEmpty { stringResource(R.string.settings_ml_none) },
                ) { onOpenDialog(ArtistPageDialog.HiddenButtons) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_arp_play_action),
                    stringResource(page.playAction.label),
                ) { onOpenDialog(ArtistPageDialog.PlayAction) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_arp_dynamic_colors),
                    stringResource(dynamicColorsLabel(page.dynamicColors)),
                ) { onOpenDialog(ArtistPageDialog.DynamicColors) }
            }
        }
    }
    item(key = "arp_content") {
        Group(R.string.settings_arp_content) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_arp_order),
                    page.sectionOrder.map { stringResource(it.label) }.joinToString(),
                ) { onAction(SettingsAction.OpenArtistSections) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_arp_albums_sort),
                    stringResource(page.albumsSort.label),
                ) { onOpenDialog(ArtistPageDialog.AlbumsSort) }
            }
            item { shapes ->
                ChoiceItem(
                    shapes,
                    stringResource(R.string.settings_arp_columns),
                    null,
                    ArtistPagePreferences.COLUMNS.toList(),
                    selected = page.albumColumns,
                    label = { it.toString() },
                    onSelect = { set(page.copy(albumColumns = it)) },
                )
            }
            item { shapes ->
                ChoiceItem(
                    shapes,
                    stringResource(R.string.settings_arp_landscape_columns),
                    null,
                    ArtistPagePreferences.LANDSCAPE_COLUMNS.toList(),
                    selected = page.landscapeAlbumColumns,
                    label = { it.toString() },
                    onSelect = { set(page.copy(landscapeAlbumColumns = it)) },
                )
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_arp_appears_on),
                    stringResource(R.string.settings_arp_appears_on_summary),
                    page.showAppearsOn,
                ) { set(page.copy(showAppearsOn = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_arp_top_tracks),
                    stringResource(R.string.settings_arp_top_tracks_summary),
                    page.showTopTracks,
                ) { set(page.copy(showTopTracks = it)) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_arp_top_tracks_count),
                    page.topTracksCount.toString(),
                    enabled = page.showTopTracks,
                ) { onOpenDialog(ArtistPageDialog.TopTracksCount) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_arp_about),
                    stringResource(R.string.settings_arp_about_summary),
                    page.showAbout,
                ) { set(page.copy(showAbout = it)) }
            }
        }
    }
    restoreDefaultsGroup { onOpenDialog(ArtistPageDialog.Restore) }
}

@Composable
internal fun ArtistPageDialogHost(
    dialog: ArtistPageDialog,
    page: ArtistPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        ArtistPageDialog.Restore -> {
            RestoreDefaultsDialog({ onAction(SettingsAction.RestoreArtistPageDefaults) }, onDismiss)
        }

        ArtistPageDialog.HiddenButtons -> {
            // The checks live here until OK; Cancel drops them.
            var hidden by rememberSaveable { mutableStateOf(page.hiddenButtons.toList()) }
            MqDialog(
                title = stringResource(dialog.title),
                confirmLabel = stringResource(R.string.settings_ok),
                onConfirm = {
                    onAction(SettingsAction.SetArtistPage(page.copy(hiddenButtons = hidden.toSet())))
                    onDismiss()
                },
                dismissLabel = stringResource(R.string.settings_cancel),
                onDismiss = onDismiss,
            ) { HiddenButtons(hidden.toSet()) { hidden = it.toList() } }
        }

        else -> {
            MqDialog(
                title = stringResource(dialog.title),
                confirmLabel = null,
                onConfirm = {},
                dismissLabel = stringResource(R.string.settings_cancel),
                onDismiss = onDismiss,
            ) { ArtistPageChoices(dialog, page, onAction, onDismiss) }
        }
    }
}

/** A dialog's content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
internal fun ArtistPageDialogContent(
    dialog: ArtistPageDialog,
    page: ArtistPagePreferences,
) {
    val ok = stringResource(R.string.settings_ok).takeIf { dialog == ArtistPageDialog.HiddenButtons }
    MqDialogContent(stringResource(dialog.title), ok, {}, stringResource(R.string.settings_cancel), {}) {
        if (dialog == ArtistPageDialog.HiddenButtons) {
            HiddenButtons(page.hiddenButtons) {}
        } else {
            ArtistPageChoices(dialog, page, {}, {})
        }
    }
}

/** "Page content order": drag the sections into order; OK applies. They cannot be unchecked: switches hide them. */
@Composable
internal fun ArtistSectionsDialog(
    draft: OrganizeDraft<ArtistSection>,
    onAction: (SettingsAction) -> Unit,
) {
    OrganizeDialog(
        stringResource(R.string.settings_arp_order),
        draft,
        label = { stringResource(it.label) },
        onToggle = {},
        onMove = { from, to -> onAction(SettingsAction.MoveArtistSection(from, to)) },
        onConfirm = { onAction(SettingsAction.ConfirmArtistSections) },
        onDismiss = { onAction(SettingsAction.DismissArtistSections) },
    )
}

/** The section order being organized: every section, all checked and fixed. */
internal fun artistSectionsDraft(order: List<ArtistSection>): OrganizeDraft<ArtistSection> =
    OrganizeDraft.of(order, ArtistSection.entries, fixed = ArtistSection.entries.toSet())

@Composable
private fun HiddenButtons(
    hidden: Set<ArtistButton>,
    onChange: (Set<ArtistButton>) -> Unit,
) {
    Column {
        ArtistButton.entries.forEach { button ->
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

/** One row per option, a check on the selected one; a tap applies it and closes the dialog. */
@Composable
private fun ArtistPageChoices(
    dialog: ArtistPageDialog,
    page: ArtistPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows: List<Pair<String, ArtistPagePreferences>> =
        when (dialog) {
            ArtistPageDialog.Background -> {
                ArtistHeaderBackground.entries.map { stringResource(it.label) to page.copy(headerBackground = it) }
            }

            ArtistPageDialog.PlayAction -> {
                ArtistPlayAction.entries.map { stringResource(it.label) to page.copy(playAction = it) }
            }

            ArtistPageDialog.AlbumsSort -> {
                MoreAlbumsSort.entries.map { stringResource(it.label) to page.copy(albumsSort = it) }
            }

            ArtistPageDialog.TopTracksCount -> {
                TOP_TRACKS_COUNTS.map { it.toString() to page.copy(topTracksCount = it) }
            }

            ArtistPageDialog.DynamicColors -> {
                listOf(false, true).map { stringResource(dynamicColorsLabel(it)) to page.copy(dynamicColors = it) }
            }

            ArtistPageDialog.HiddenButtons, ArtistPageDialog.Restore -> {
                emptyList()
            }
        }
    Column {
        rows.forEach { (label, choice) ->
            val selected = choice == page
            ListItem(
                headlineContent = { Text(label) },
                trailingContent = if (selected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier =
                    Modifier.selectable(selected, role = Role.RadioButton) {
                        onAction(SettingsAction.SetArtistPage(choice))
                        onDismiss()
                    },
            )
        }
    }
}

/** The "Max items" choices, within 5-50. */
private val TOP_TRACKS_COUNTS = listOf(5, 10, 15, 20, 30, 50)

private fun dynamicColorsLabel(on: Boolean): Int =
    if (on) R.string.settings_arp_dynamic_colors_image else R.string.settings_arp_dynamic_colors_off

private val ArtistPageDialog.title: Int
    get() =
        when (this) {
            ArtistPageDialog.Background -> R.string.settings_arp_background
            ArtistPageDialog.HiddenButtons -> R.string.settings_arp_hidden_buttons
            ArtistPageDialog.PlayAction -> R.string.settings_arp_play_action
            ArtistPageDialog.AlbumsSort -> R.string.settings_arp_albums_sort
            ArtistPageDialog.TopTracksCount -> R.string.settings_arp_top_tracks_count
            ArtistPageDialog.DynamicColors -> R.string.settings_arp_dynamic_colors
            ArtistPageDialog.Restore -> R.string.settings_restore
        }

private val ArtistHeaderBackground.label: Int
    get() =
        when (this) {
            ArtistHeaderBackground.Backdrop -> R.string.settings_arp_background_backdrop
            ArtistHeaderBackground.ArtistImage -> R.string.settings_arp_background_image
            ArtistHeaderBackground.ImageColor -> R.string.settings_arp_background_color
            ArtistHeaderBackground.None -> R.string.settings_ap_background_none
        }

private val ArtistButton.label: Int
    get() =
        when (this) {
            ArtistButton.Shuffle -> R.string.settings_ap_button_shuffle
            ArtistButton.PlayNext -> R.string.settings_ap_button_play_next
            ArtistButton.AddToQueue -> R.string.settings_ap_button_add_to_queue
            ArtistButton.Favorite -> R.string.settings_ap_button_favorite
        }

private val ArtistPlayAction.label: Int
    get() =
        when (this) {
            ArtistPlayAction.Albums -> R.string.settings_arp_play_albums
            ArtistPlayAction.TopTracks -> R.string.settings_arp_play_top
            ArtistPlayAction.AllShuffled -> R.string.settings_arp_play_shuffled
        }

internal val ArtistSection.label: Int
    get() =
        when (this) {
            ArtistSection.Albums -> R.string.settings_arp_section_albums
            ArtistSection.AppearsOn -> R.string.settings_arp_section_appears_on
            ArtistSection.TopTracks -> R.string.settings_arp_section_top_tracks
            ArtistSection.About -> R.string.settings_ap_section_about
        }
