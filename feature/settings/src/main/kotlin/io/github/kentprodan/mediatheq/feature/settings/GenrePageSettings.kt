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
import io.github.kentprodan.mediatheq.core.data.GenreButton
import io.github.kentprodan.mediatheq.core.data.GenreHeaderBackground
import io.github.kentprodan.mediatheq.core.data.GenrePagePreferences
import io.github.kentprodan.mediatheq.core.data.GenreSection
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** The dialogs of Settings -> Genre page (the section order is ViewModel state, like the Artist page's). */
enum class GenrePageDialog { Background, HiddenButtons, TopTracksCount, DynamicColors, Restore }

/** Settings -> Interface -> Page settings -> Genre page (#134): every change applies at once. */
internal fun LazyListScope.genrePageGroups(
    page: GenrePagePreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (GenrePageDialog) -> Unit,
) {
    val set = { changed: GenrePagePreferences -> onAction(SettingsAction.SetGenrePage(changed)) }
    item(key = "gp_header") {
        Group(R.string.settings_gp_header) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_gp_background),
                    stringResource(page.headerBackground.label),
                ) { onOpenDialog(GenrePageDialog.Background) }
            }
            item { shapes ->
                val hidden = GenreButton.entries.filter { it in page.hiddenButtons }.map { stringResource(it.label) }
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_gp_hidden_buttons),
                    hidden.joinToString(", ").ifEmpty { stringResource(R.string.settings_ml_none) },
                ) { onOpenDialog(GenrePageDialog.HiddenButtons) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_gp_dynamic_colors),
                    stringResource(genreColorsLabel(page.dynamicColors)),
                ) { onOpenDialog(GenrePageDialog.DynamicColors) }
            }
        }
    }
    item(key = "gp_content") {
        Group(R.string.settings_gp_content) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_gp_order),
                    page.sectionOrder.map { stringResource(it.label) }.joinToString(),
                ) { onAction(SettingsAction.OpenGenreSections) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_gp_albums),
                    stringResource(R.string.settings_gp_albums_summary),
                    page.showAlbums,
                ) { set(page.copy(showAlbums = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_gp_artists),
                    stringResource(R.string.settings_gp_artists_summary),
                    page.showArtists,
                ) { set(page.copy(showArtists = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_gp_album_artists),
                    stringResource(R.string.settings_gp_album_artists_summary),
                    page.showAlbumArtists,
                ) { set(page.copy(showAlbumArtists = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_gp_top_tracks),
                    stringResource(R.string.settings_gp_top_tracks_summary),
                    page.showTopTracks,
                ) { set(page.copy(showTopTracks = it)) }
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_arp_top_tracks_count),
                    page.topTracksCount.toString(),
                    enabled = page.showTopTracks,
                ) { onOpenDialog(GenrePageDialog.TopTracksCount) }
            }
        }
    }
    restoreDefaultsGroup { onOpenDialog(GenrePageDialog.Restore) }
}

@Composable
internal fun GenrePageDialogHost(
    dialog: GenrePageDialog,
    page: GenrePagePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        GenrePageDialog.Restore -> {
            RestoreDefaultsDialog({ onAction(SettingsAction.RestoreGenrePageDefaults) }, onDismiss)
        }

        GenrePageDialog.HiddenButtons -> {
            // The checks live here until OK; Cancel drops them.
            var hidden by rememberSaveable { mutableStateOf(page.hiddenButtons.toList()) }
            MqDialog(
                title = stringResource(dialog.title),
                confirmLabel = stringResource(R.string.settings_ok),
                onConfirm = {
                    onAction(SettingsAction.SetGenrePage(page.copy(hiddenButtons = hidden.toSet())))
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
            ) { GenrePageChoices(dialog, page, onAction, onDismiss) }
        }
    }
}

/** A dialog's content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
internal fun GenrePageDialogContent(
    dialog: GenrePageDialog,
    page: GenrePagePreferences,
) {
    val ok = stringResource(R.string.settings_ok).takeIf { dialog == GenrePageDialog.HiddenButtons }
    MqDialogContent(stringResource(dialog.title), ok, {}, stringResource(R.string.settings_cancel), {}) {
        if (dialog == GenrePageDialog.HiddenButtons) {
            HiddenButtons(page.hiddenButtons) {}
        } else {
            GenrePageChoices(dialog, page, {}, {})
        }
    }
}

/** "Page content order": drag the sections into order; OK applies. They cannot be unchecked: switches hide them. */
@Composable
internal fun GenreSectionsDialog(
    draft: OrganizeDraft<GenreSection>,
    onAction: (SettingsAction) -> Unit,
) {
    OrganizeDialog(
        stringResource(R.string.settings_gp_order),
        draft,
        label = { stringResource(it.label) },
        onToggle = {},
        onMove = { from, to -> onAction(SettingsAction.MoveGenreSection(from, to)) },
        onConfirm = { onAction(SettingsAction.ConfirmGenreSections) },
        onDismiss = { onAction(SettingsAction.DismissGenreSections) },
    )
}

/** The section order being organized: every section, all checked and fixed. */
internal fun genreSectionsDraft(order: List<GenreSection>): OrganizeDraft<GenreSection> =
    OrganizeDraft.of(order, GenreSection.entries, fixed = GenreSection.entries.toSet())

@Composable
private fun HiddenButtons(
    hidden: Set<GenreButton>,
    onChange: (Set<GenreButton>) -> Unit,
) {
    Column {
        GenreButton.entries.forEach { button ->
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
private fun GenrePageChoices(
    dialog: GenrePageDialog,
    page: GenrePagePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows: List<Pair<String, GenrePagePreferences>> =
        when (dialog) {
            GenrePageDialog.Background -> {
                GenreHeaderBackground.entries.map { stringResource(it.label) to page.copy(headerBackground = it) }
            }

            GenrePageDialog.TopTracksCount -> {
                GENRE_TOP_TRACKS_COUNTS.map { it.toString() to page.copy(topTracksCount = it) }
            }

            GenrePageDialog.DynamicColors -> {
                listOf(false, true).map { stringResource(genreColorsLabel(it)) to page.copy(dynamicColors = it) }
            }

            GenrePageDialog.HiddenButtons, GenrePageDialog.Restore -> {
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
                        onAction(SettingsAction.SetGenrePage(choice))
                        onDismiss()
                    },
            )
        }
    }
}

/** The "Max items" choices, within 5-50. */
private val GENRE_TOP_TRACKS_COUNTS = listOf(5, 10, 15, 20, 30, 50)

private fun genreColorsLabel(on: Boolean): Int =
    if (on) R.string.settings_gp_dynamic_colors_image else R.string.settings_arp_dynamic_colors_off

private val GenrePageDialog.title: Int
    get() =
        when (this) {
            GenrePageDialog.Background -> R.string.settings_gp_background
            GenrePageDialog.HiddenButtons -> R.string.settings_gp_hidden_buttons
            GenrePageDialog.TopTracksCount -> R.string.settings_arp_top_tracks_count
            GenrePageDialog.DynamicColors -> R.string.settings_gp_dynamic_colors
            GenrePageDialog.Restore -> R.string.settings_restore
        }

private val GenreHeaderBackground.label: Int
    get() =
        when (this) {
            GenreHeaderBackground.Image -> R.string.settings_gp_background_image
            GenreHeaderBackground.ImageColor -> R.string.settings_arp_background_color
            GenreHeaderBackground.None -> R.string.settings_ap_background_none
        }

private val GenreButton.label: Int
    get() =
        when (this) {
            GenreButton.Shuffle -> R.string.settings_ap_button_shuffle
            GenreButton.PlayNext -> R.string.settings_ap_button_play_next
            GenreButton.AddToQueue -> R.string.settings_ap_button_add_to_queue
        }

internal val GenreSection.label: Int
    get() =
        when (this) {
            GenreSection.Artists -> R.string.settings_gp_section_artists
            GenreSection.AlbumArtists -> R.string.settings_gp_section_album_artists
            GenreSection.Albums -> R.string.settings_arp_section_albums
            GenreSection.TopTracks -> R.string.settings_arp_section_top_tracks
        }
