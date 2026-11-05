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
import io.github.kentprodan.mediatheq.core.data.AlbumButton
import io.github.kentprodan.mediatheq.core.data.AlbumHeaderBackground
import io.github.kentprodan.mediatheq.core.data.AlbumPagePreferences
import io.github.kentprodan.mediatheq.core.data.AlbumSection
import io.github.kentprodan.mediatheq.core.data.MoreAlbumsSort
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** The dialogs of Settings -> Album page (the section order is ViewModel state, like the Library page's tiles). */
enum class AlbumPageDialog { Background, HiddenButtons, MoreSort, Restore }

/** Settings -> Interface -> Page settings -> Album page (#132): every change applies at once. */
internal fun LazyListScope.albumPageGroups(
    page: AlbumPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (AlbumPageDialog) -> Unit,
) {
    val set = { changed: AlbumPagePreferences -> onAction(SettingsAction.SetAlbumPage(changed)) }
    item(key = "ap_header") {
        Group(R.string.settings_ap_header) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_ap_background),
                    stringResource(page.headerBackground.label),
                ) {
                    onOpenDialog(AlbumPageDialog.Background)
                }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ap_quality),
                    stringResource(R.string.settings_ap_quality_summary),
                    page.showQuality,
                ) { set(page.copy(showQuality = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ap_rating),
                    stringResource(R.string.settings_ap_rating_summary),
                    page.showRatingBar,
                ) { set(page.copy(showRatingBar = it)) }
            }
            item { shapes ->
                val hidden = AlbumButton.entries.filter { it in page.hiddenButtons }.map { stringResource(it.label) }
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_ap_hidden_buttons),
                    hidden.joinToString(", ").ifEmpty { stringResource(R.string.settings_ml_none) },
                ) { onOpenDialog(AlbumPageDialog.HiddenButtons) }
            }
        }
    }
    item(key = "ap_content") {
        Group(R.string.settings_ap_content) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_ap_order),
                    page.sectionOrder.map { stringResource(it.label) }.joinToString(),
                ) { onAction(SettingsAction.OpenAlbumSections) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ap_more),
                    stringResource(R.string.settings_ap_more_summary),
                    page.showMoreFromArtist,
                ) { set(page.copy(showMoreFromArtist = it)) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_ap_more_sort),
                    stringResource(page.moreAlbumsSort.label),
                    enabled = page.showMoreFromArtist,
                ) { onOpenDialog(AlbumPageDialog.MoreSort) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ap_about),
                    stringResource(R.string.settings_ap_about_summary),
                    page.showAbout,
                ) { set(page.copy(showAbout = it)) }
            }
        }
    }
    item(key = "ap_tracks") {
        Group(R.string.settings_ap_tracks) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ap_track_artists),
                    stringResource(R.string.settings_ap_track_artists_summary),
                    page.alwaysShowTrackArtists,
                ) { set(page.copy(alwaysShowTrackArtists = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ap_track_numbers),
                    stringResource(R.string.settings_ap_track_numbers_summary),
                    page.showTrackNumbers,
                ) { set(page.copy(showTrackNumbers = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ap_thumbnails),
                    stringResource(R.string.settings_ap_thumbnails_summary),
                    page.showTrackThumbnails,
                ) { set(page.copy(showTrackThumbnails = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ap_multiline),
                    stringResource(R.string.settings_ap_multiline_summary),
                    page.multilineTrackTitles,
                ) { set(page.copy(multilineTrackTitles = it)) }
            }
        }
    }
    restoreDefaultsGroup { onOpenDialog(AlbumPageDialog.Restore) }
}

@Composable
internal fun AlbumPageDialogHost(
    dialog: AlbumPageDialog,
    page: AlbumPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        AlbumPageDialog.Restore -> {
            RestoreDefaultsDialog({ onAction(SettingsAction.RestoreAlbumPageDefaults) }, onDismiss)
        }

        AlbumPageDialog.HiddenButtons -> {
            // The checks live here until OK; Cancel drops them.
            var hidden by rememberSaveable { mutableStateOf(page.hiddenButtons.toList()) }
            MqDialog(
                title = stringResource(dialog.title),
                confirmLabel = stringResource(R.string.settings_ok),
                onConfirm = {
                    onAction(SettingsAction.SetAlbumPage(page.copy(hiddenButtons = hidden.toSet())))
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
            ) { AlbumPageChoices(dialog, page, onAction, onDismiss) }
        }
    }
}

/** A dialog's content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
internal fun AlbumPageDialogContent(
    dialog: AlbumPageDialog,
    page: AlbumPagePreferences,
) {
    val ok = stringResource(R.string.settings_ok).takeIf { dialog == AlbumPageDialog.HiddenButtons }
    MqDialogContent(stringResource(dialog.title), ok, {}, stringResource(R.string.settings_cancel), {}) {
        if (dialog == AlbumPageDialog.HiddenButtons) {
            HiddenButtons(page.hiddenButtons) {}
        } else {
            AlbumPageChoices(dialog, page, {}, {})
        }
    }
}

/** "Page content order": drag the sections into order; OK applies. They cannot be unchecked: switches hide them. */
@Composable
internal fun AlbumSectionsDialog(
    draft: OrganizeDraft<AlbumSection>,
    onAction: (SettingsAction) -> Unit,
) {
    OrganizeDialog(
        stringResource(R.string.settings_ap_order),
        draft,
        label = { stringResource(it.label) },
        onToggle = {},
        onMove = { from, to -> onAction(SettingsAction.MoveAlbumSection(from, to)) },
        onConfirm = { onAction(SettingsAction.ConfirmAlbumSections) },
        onDismiss = { onAction(SettingsAction.DismissAlbumSections) },
    )
}

/** The section order being organized: every section, all checked and fixed. */
internal fun albumSectionsDraft(order: List<AlbumSection>): OrganizeDraft<AlbumSection> =
    OrganizeDraft.of(order, AlbumSection.entries, fixed = AlbumSection.entries.toSet())

@Composable
private fun HiddenButtons(
    hidden: Set<AlbumButton>,
    onChange: (Set<AlbumButton>) -> Unit,
) {
    Column {
        AlbumButton.entries.forEach { button ->
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
private fun AlbumPageChoices(
    dialog: AlbumPageDialog,
    page: AlbumPagePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows: List<Pair<Int, AlbumPagePreferences>> =
        when (dialog) {
            AlbumPageDialog.Background -> {
                AlbumHeaderBackground.entries.map {
                    it.label to
                        page.copy(headerBackground = it)
                }
            }

            AlbumPageDialog.MoreSort -> {
                MoreAlbumsSort.entries.map { it.label to page.copy(moreAlbumsSort = it) }
            }

            AlbumPageDialog.HiddenButtons, AlbumPageDialog.Restore -> {
                emptyList()
            }
        }
    Column {
        rows.forEach { (label, choice) ->
            val selected = choice == page
            ListItem(
                headlineContent = { Text(stringResource(label)) },
                trailingContent = if (selected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier =
                    Modifier.selectable(selected, role = Role.RadioButton) {
                        onAction(SettingsAction.SetAlbumPage(choice))
                        onDismiss()
                    },
            )
        }
    }
}

private val AlbumPageDialog.title: Int
    get() =
        when (this) {
            AlbumPageDialog.Background -> R.string.settings_ap_background
            AlbumPageDialog.HiddenButtons -> R.string.settings_ap_hidden_buttons
            AlbumPageDialog.MoreSort -> R.string.settings_ap_more_sort
            AlbumPageDialog.Restore -> R.string.settings_restore
        }

private val AlbumHeaderBackground.label: Int
    get() =
        when (this) {
            AlbumHeaderBackground.CoverImage -> R.string.settings_ap_background_image
            AlbumHeaderBackground.CoverColor -> R.string.settings_ap_background_color
            AlbumHeaderBackground.None -> R.string.settings_ap_background_none
        }

private val AlbumButton.label: Int
    get() =
        when (this) {
            AlbumButton.Resume -> R.string.settings_ap_button_resume
            AlbumButton.Shuffle -> R.string.settings_ap_button_shuffle
            AlbumButton.PlayNext -> R.string.settings_ap_button_play_next
            AlbumButton.AddToQueue -> R.string.settings_ap_button_add_to_queue
            AlbumButton.Favorite -> R.string.settings_ap_button_favorite
        }

internal val AlbumSection.label: Int
    get() =
        when (this) {
            AlbumSection.Tracks -> R.string.settings_ap_section_tracks
            AlbumSection.MoreFromArtist -> R.string.settings_ap_section_more
            AlbumSection.About -> R.string.settings_ap_section_about
        }

private val MoreAlbumsSort.label: Int
    get() =
        when (this) {
            MoreAlbumsSort.YearDescending -> R.string.settings_ap_sort_year_desc
            MoreAlbumsSort.YearAscending -> R.string.settings_ap_sort_year_asc
            MoreAlbumsSort.Title -> R.string.settings_ap_sort_title
        }
