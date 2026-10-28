package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
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
import io.github.kentprodan.mediatheq.core.data.AppFont
import io.github.kentprodan.mediatheq.core.data.ColorSource
import io.github.kentprodan.mediatheq.core.data.NowPlayingColors
import io.github.kentprodan.mediatheq.core.data.ThemeMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.designsystem.component.MqColorSwatch
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqSeedPresets
import io.github.kentprodan.mediatheq.core.designsystem.theme.seedHue
import io.github.kentprodan.mediatheq.core.designsystem.theme.seedOfHue
import kotlin.math.roundToInt

/** The dialogs of Settings -> Theme, font and colors. */
enum class ThemeDialog { NowPlayingColors, Font, Restore }

/** Settings -> Interface -> Theme, font and colors (#121): every change applies at once, app-wide. */
internal fun LazyListScope.themeGroups(
    theme: ThemePreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (ThemeDialog) -> Unit,
) {
    val set = { changed: ThemePreferences -> onAction(SettingsAction.SetTheme(changed)) }
    item(key = "theme") {
        Group(R.string.settings_theme_group) {
            item { shapes ->
                ChoiceItem(
                    shapes,
                    stringResource(R.string.settings_theme_mode),
                    summary = null,
                    ThemeMode.entries,
                    selected = theme.mode,
                    label = { stringResource(it.label) },
                    onSelect = { set(theme.copy(mode = it)) },
                )
            }
            item { shapes ->
                ChoiceItem(
                    shapes,
                    stringResource(R.string.settings_color_source),
                    summary = null,
                    ColorSource.entries,
                    selected = theme.colorSource,
                    label = { stringResource(it.label) },
                    onSelect = { set(theme.copy(colorSource = it)) },
                )
            }
            if (theme.colorSource == ColorSource.Custom) {
                item { shapes -> CustomColorItem(shapes, theme.customSeed) { set(theme.copy(customSeed = it)) } }
            }
            item { shapes -> ThemeColorsItem(shapes) }
        }
    }
    item(key = "now_playing") {
        Group(R.string.settings_now_playing) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_now_playing_colors),
                    stringResource(theme.nowPlayingColors.label),
                ) {
                    onOpenDialog(ThemeDialog.NowPlayingColors)
                }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_ignore_surfaces),
                    stringResource(R.string.settings_ignore_surfaces_summary),
                    checked = theme.ignoreSurfaces,
                    onCheckedChange = { set(theme.copy(ignoreSurfaces = it)) },
                )
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_instant_colors),
                    stringResource(R.string.settings_instant_colors_summary),
                    checked = theme.instantColors,
                    onCheckedChange = { set(theme.copy(instantColors = it)) },
                )
            }
        }
    }
    item(key = "typography") {
        Group(R.string.settings_typography) {
            item { shapes ->
                DialogItem(shapes, stringResource(R.string.settings_typography), theme.font.label()) {
                    onOpenDialog(ThemeDialog.Font)
                }
            }
        }
    }
    restoreDefaultsGroup { onOpenDialog(ThemeDialog.Restore) }
}

/** "Restore defaults" in its own group at the bottom of a settings page; it asks first ([RestoreDefaultsDialog]). */
internal fun LazyListScope.restoreDefaultsGroup(onClick: () -> Unit) {
    item(key = "restore") {
        Group(R.string.settings_restore_group) {
            item { shapes ->
                SegmentedListItem(
                    onClick = onClick,
                    shapes = shapes,
                    colors = settingsColors(),
                    leadingContent = { Icon(MqIcons.Restore, contentDescription = null) },
                ) { Text(stringResource(R.string.settings_restore)) }
            }
        }
    }
}

/** Asks before a page's settings go back to their defaults; nothing changes on Cancel. */
@Composable
internal fun RestoreDefaultsDialog(
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    MqDialog(
        title = null,
        confirmLabel = stringResource(R.string.settings_restore_confirm),
        onConfirm = {
            onRestore()
            onDismiss()
        },
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
    ) { Text(stringResource(R.string.settings_restore_message)) }
}

@Composable
internal fun ThemeDialogHost(
    dialog: ThemeDialog,
    theme: ThemePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        ThemeDialog.Restore -> {
            RestoreDefaultsDialog({ onAction(SettingsAction.RestoreThemeDefaults) }, onDismiss)
        }

        ThemeDialog.NowPlayingColors, ThemeDialog.Font -> {
            MqDialog(
                title = stringResource(dialog.title),
                confirmLabel = null,
                onConfirm = {},
                dismissLabel = stringResource(R.string.settings_cancel),
                onDismiss = onDismiss,
            ) { ThemeChoices(dialog, theme, onAction, onDismiss) }
        }
    }
}

/** A choice dialog's content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
internal fun ThemeChoiceDialogContent(
    dialog: ThemeDialog,
    theme: ThemePreferences,
) {
    MqDialogContent(stringResource(dialog.title), null, {}, stringResource(R.string.settings_cancel), {}) {
        ThemeChoices(dialog, theme, {}, {})
    }
}

private val ThemeDialog.title: Int
    get() =
        when (this) {
            ThemeDialog.NowPlayingColors -> R.string.settings_now_playing_colors
            ThemeDialog.Font -> R.string.settings_typography
            ThemeDialog.Restore -> R.string.settings_restore
        }

/** One row per option, a check on the selected one; a tap applies it and closes the dialog. */
@Composable
private fun ThemeChoices(
    dialog: ThemeDialog,
    theme: ThemePreferences,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows: List<Pair<String, ThemePreferences>> =
        when (dialog) {
            ThemeDialog.NowPlayingColors -> {
                NowPlayingColors.entries.map {
                    stringResource(it.label) to
                        theme.copy(nowPlayingColors = it)
                }
            }

            ThemeDialog.Font -> {
                AppFont.entries.map { it.label() to theme.copy(font = it) }
            }

            ThemeDialog.Restore -> {
                emptyList()
            }
        }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        rows.forEach { (label, choice) ->
            val selected = choice == theme
            ListItem(
                headlineContent = { Text(label) },
                trailingContent = if (selected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier =
                    Modifier.selectable(selected, role = Role.RadioButton) {
                        onAction(SettingsAction.SetTheme(choice))
                        onDismiss()
                    },
            )
        }
    }
}

/** Preset seeds, then a hue slider (saved on release, like the equalizer); the whole scheme comes from the seed. */
@Composable
private fun CustomColorItem(
    shapes: ListItemShapes,
    seed: Int,
    onSeed: (Int) -> Unit,
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val hueLabel = stringResource(R.string.settings_custom_hue)
    SegmentedListItem(
        shapes = shapes,
        colors = settingsColors(),
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
                Text(stringResource(R.string.settings_custom_color_summary))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
                    MqSeedPresets.forEachIndexed { index, preset ->
                        MqColorSwatch(
                            Color(preset),
                            stringResource(R.string.settings_preset_color, index + 1, MqSeedPresets.size),
                            selected = preset == seed,
                            onClick = { onSeed(preset) },
                        )
                    }
                }
                Slider(
                    value = dragged ?: seedHue(seed),
                    onValueChange = { dragged = it },
                    onValueChangeFinished = {
                        dragged?.let { onSeed(seedOfHue(it)) }
                        dragged = null
                    },
                    valueRange = 0f..HUE_MAX,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = hueLabel },
                )
            }
        },
    ) { Text(stringResource(R.string.settings_color_custom)) }
}

/** The active scheme's main roles, read from the theme on screen. */
@Composable
private fun ThemeColorsItem(shapes: ListItemShapes) {
    val colors = MaterialTheme.colorScheme
    val roles =
        listOf(
            R.string.settings_role_primary to colors.primary,
            R.string.settings_role_secondary to colors.secondary,
            R.string.settings_role_tertiary to colors.tertiary,
            R.string.settings_role_primary_container to colors.primaryContainer,
            R.string.settings_role_secondary_container to colors.secondaryContainer,
            R.string.settings_role_tertiary_container to colors.tertiaryContainer,
            R.string.settings_role_surface to colors.surface,
            R.string.settings_role_surface_container to colors.surfaceContainer,
        )
    SegmentedListItem(
        shapes = shapes,
        colors = settingsColors(),
        supportingContent = {
            FlowRow(
                Modifier.padding(top = MediatheqSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
            ) { roles.forEach { (name, color) -> MqColorSwatch(color, stringResource(name)) } }
        },
    ) { Text(stringResource(R.string.settings_theme_colors)) }
}

/** A row that opens a choice dialog; the summary is the current value. */
@Composable
private fun DialogItem(
    shapes: ListItemShapes,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = settingsColors(),
        supportingContent = { Text(value) },
    ) { Text(title) }
}

private val ThemeMode.label: Int
    get() =
        when (this) {
            ThemeMode.System -> R.string.settings_mode_system
            ThemeMode.Light -> R.string.settings_mode_light
            ThemeMode.Dark -> R.string.settings_mode_dark
            ThemeMode.Black -> R.string.settings_mode_black
            ThemeMode.SystemBlack -> R.string.settings_mode_system_black
        }

private val ColorSource.label: Int
    get() =
        when (this) {
            ColorSource.Iris -> R.string.settings_color_iris
            ColorSource.MaterialYou -> R.string.settings_color_material_you
            ColorSource.Custom -> R.string.settings_color_custom
        }

private val NowPlayingColors.label: Int
    get() =
        when (this) {
            NowPlayingColors.Off -> R.string.settings_now_playing_off
            NowPlayingColors.Cover -> R.string.settings_now_playing_cover
            NowPlayingColors.CoverVibrant -> R.string.settings_now_playing_cover_vibrant
        }

// Font names are names: not translated.
@Composable
private fun AppFont.label(): String =
    when (this) {
        AppFont.System -> stringResource(R.string.settings_font_system)
        AppFont.Inter -> "Inter"
        AppFont.Lato -> "Lato"
        AppFont.Nunito -> "Nunito"
        AppFont.Roboto -> "Roboto"
        AppFont.RobotoSlab -> "Roboto Slab"
        AppFont.LexendDeca -> "Lexend Deca"
        AppFont.BitterRubik -> stringResource(R.string.settings_font_pair, "Bitter", "Rubik")
        AppFont.BebasNeue -> stringResource(R.string.settings_font_headings, "Bebas Neue")
        AppFont.CastoroTitling -> stringResource(R.string.settings_font_headings, "Castoro Titling")
    }

private const val HUE_MAX = 360f
