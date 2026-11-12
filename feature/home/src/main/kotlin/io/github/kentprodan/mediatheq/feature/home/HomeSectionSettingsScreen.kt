package io.github.kentprodan.mediatheq.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.HomeDisplay
import io.github.kentprodan.mediatheq.core.model.HomeKind
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.HomeSectionSettings
import io.github.kentprodan.mediatheq.core.ui.title
import kotlin.math.roundToInt

@Composable
internal fun HomeSectionSettingsRoute(
    section: HomeSection,
    onBack: () -> Unit,
    viewModel: HomeSectionSettingsViewModel =
        hiltViewModel<HomeSectionSettingsViewModel, HomeSectionSettingsViewModel.Factory>(
            key = section.name,
            creationCallback = { it.create(section) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeSectionSettingsScreen(section, state, viewModel::onAction, onBack)
}

/** What a dialog of the page is open for. */
internal enum class SectionDialog {
    Display,
    CenterText,
    FavoriteOverlay,
    OfflineOverlay,
    CopyFrom,
    ApplyToAll,
    Restore,
}

/**
 * Settings -> Home page -> a section's gear (#182): the section as Home shows it, then only the settings that apply to
 * its kind and display (Carousel draws its own covers: no card options), Copy from, Apply to all, Restore defaults.
 * Stateless but for the open dialog.
 */
@Composable
fun HomeSectionSettingsScreen(
    section: HomeSection,
    state: HomeSectionSettingsUiState,
    onAction: (HomeSectionSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<SectionDialog?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(section.title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.home_section_back))
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (state !is HomeSectionSettingsUiState.Content) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = MediatheqSpacing.l,
                ).plusBottomBars(),
        ) {
            item(key = "preview-title") { GroupTitle(R.string.home_section_preview) }
            if (state.preview.items.isEmpty) {
                item(key = "preview-empty") {
                    Text(
                        stringResource(R.string.home_section_preview_empty),
                        Modifier.padding(horizontal = MediatheqSpacing.l),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Home's own code: the preview is the section as it will look (taps do nothing here).
                section(state.preview, emptySet(), {}, HomeNavigation())
            }
            settingsGroups(state, onAction) { dialog = it }
        }
    }
    if (state is HomeSectionSettingsUiState.Content) {
        dialog?.let { SectionDialogHost(it, state, onAction) { dialog = null } }
    }
}

private fun LazyListScope.settingsGroups(
    state: HomeSectionSettingsUiState.Content,
    onAction: (HomeSectionSettingsAction) -> Unit,
    onOpen: (SectionDialog) -> Unit,
) {
    val settings = state.settings
    val kind = state.section.kind
    val set = { change: (HomeSectionSettings) -> HomeSectionSettings ->
        onAction(HomeSectionSettingsAction.Set(change))
    }
    val cards = kind != HomeKind.Tracks && kind != HomeKind.Shortcuts
    val cardOptions = cards && settings.display != HomeDisplay.Carousel
    item(key = "settings") {
        Group(R.string.home_section_settings) {
            if (cards) {
                row { shapes ->
                    ChoiceRow(shapes, R.string.home_section_display, displayLabel(settings.display)) {
                        onOpen(SectionDialog.Display)
                    }
                }
            }
            if (cards && settings.display == HomeDisplay.Grid) {
                row { shapes -> RowsRow(shapes, settings.gridRows) { set { s -> s.copy(gridRows = it) } } }
            }
            if (kind != HomeKind.Shortcuts) {
                row { shapes -> MaxItemsRow(shapes, settings.maxItems) { set { s -> s.copy(maxItems = it) } } }
            }
            row { shapes ->
                SwitchRow(
                    shapes,
                    R.string.home_section_header,
                    settings.showHeader,
                ) { set { s -> s.copy(showHeader = it) } }
            }
            if (kind != HomeKind.Shortcuts) {
                row { shapes ->
                    SwitchRow(
                        shapes,
                        R.string.home_section_more,
                        settings.moreIndicator,
                        enabled = settings.showHeader,
                    ) {
                        set { s -> s.copy(moreIndicator = it) }
                    }
                }
            }
            // Artist cards are always centered.
            if (cardOptions && kind != HomeKind.Artists) {
                row { shapes ->
                    ChoiceRow(
                        shapes,
                        R.string.home_section_center,
                        triLabel(settings.centerText),
                    ) { onOpen(SectionDialog.CenterText) }
                }
            }
            if ((cardOptions && kind != HomeKind.Artists) || kind == HomeKind.Tracks) {
                row { shapes ->
                    SwitchRow(shapes, R.string.home_section_subtitles, settings.hideSubtitles) {
                        set { s -> s.copy(hideSubtitles = it) }
                    }
                }
            }
            if (cardOptions) {
                row { shapes ->
                    SwitchRow(
                        shapes,
                        R.string.home_section_play,
                        settings.playButton,
                    ) { set { s -> s.copy(playButton = it) } }
                }
            }
            // Only album covers draw the status marks.
            if (cardOptions && kind == HomeKind.Albums) {
                row { shapes ->
                    ChoiceRow(shapes, R.string.home_section_favorite, triLabel(settings.favoriteOverlay)) {
                        onOpen(SectionDialog.FavoriteOverlay)
                    }
                }
                row { shapes ->
                    ChoiceRow(shapes, R.string.home_section_offline, triLabel(settings.offlineOverlay)) {
                        onOpen(SectionDialog.OfflineOverlay)
                    }
                }
            }
            // Shortcuts are never empty.
            if (kind != HomeKind.Shortcuts) {
                row { shapes ->
                    SwitchRow(shapes, R.string.home_section_hide_empty, settings.hideIfEmpty) {
                        set { s -> s.copy(hideIfEmpty = it) }
                    }
                }
            }
        }
    }
    item(key = "actions") {
        Group(R.string.home_section_actions) {
            if (state.copyFrom.isNotEmpty()) {
                row { shapes ->
                    ActionRow(
                        shapes,
                        MqIcons.Checklist,
                        R.string.home_section_copy_from,
                    ) { onOpen(SectionDialog.CopyFrom) }
                }
                row { shapes ->
                    ActionRow(
                        shapes,
                        MqIcons.Sync,
                        R.string.home_section_apply_all,
                    ) { onOpen(SectionDialog.ApplyToAll) }
                }
            }
            row { shapes ->
                ActionRow(shapes, MqIcons.Restore, R.string.home_section_restore) { onOpen(SectionDialog.Restore) }
            }
        }
    }
}

@Composable
private fun SectionDialogHost(
    dialog: SectionDialog,
    state: HomeSectionSettingsUiState.Content,
    onAction: (HomeSectionSettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val settings = state.settings
    val set = { change: (HomeSectionSettings) -> HomeSectionSettings ->
        onAction(HomeSectionSettingsAction.Set(change))
    }
    when (dialog) {
        SectionDialog.Display -> {
            ChoiceDialog(
                R.string.home_section_display,
                listOf(HomeDisplay.Row, HomeDisplay.Grid, HomeDisplay.Carousel),
                settings.display,
                { displayLabel(it) },
                { set { s -> s.copy(display = it) } },
                onDismiss,
            )
        }

        SectionDialog.CenterText -> {
            TriDialog(
                R.string.home_section_center,
                settings.centerText,
                { set { s -> s.copy(centerText = it) } },
                onDismiss,
            )
        }

        SectionDialog.FavoriteOverlay -> {
            TriDialog(R.string.home_section_favorite, settings.favoriteOverlay, {
                set { s -> s.copy(favoriteOverlay = it) }
            }, onDismiss)
        }

        SectionDialog.OfflineOverlay -> {
            TriDialog(R.string.home_section_offline, settings.offlineOverlay, {
                set { s -> s.copy(offlineOverlay = it) }
            }, onDismiss)
        }

        SectionDialog.CopyFrom -> {
            ChoiceDialog(
                R.string.home_section_copy_from,
                state.copyFrom,
                null,
                { stringResource(it.title) },
                { onAction(HomeSectionSettingsAction.CopyFrom(it)) },
                onDismiss,
            )
        }

        SectionDialog.ApplyToAll -> {
            ConfirmDialog(R.string.home_section_apply_all_confirm, R.string.home_section_apply, onDismiss) {
                onAction(HomeSectionSettingsAction.ApplyToAll)
            }
        }

        SectionDialog.Restore -> {
            ConfirmDialog(R.string.home_section_restore_confirm, R.string.home_section_restore_button, onDismiss) {
                onAction(HomeSectionSettingsAction.Restore)
            }
        }
    }
}

/** The rows of one titled group, sharing the segmented shapes. */
private class RowsScope {
    val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    fun row(content: @Composable (ListItemShapes) -> Unit) {
        rows += content
    }
}

@Composable
private fun Group(
    title: Int,
    content: RowsScope.() -> Unit,
) {
    val rows = RowsScope().apply(content).rows
    Column(
        Modifier.padding(horizontal = MediatheqSpacing.l),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        GroupTitle(title, Modifier)
        rows.forEachIndexed { index, row -> row(ListItemDefaults.segmentedShapes(index, rows.size)) }
    }
}

@Composable
private fun GroupTitle(
    title: Int,
    modifier: Modifier = Modifier.padding(horizontal = MediatheqSpacing.l),
) {
    Text(
        stringResource(title),
        modifier.padding(top = MediatheqSpacing.l, bottom = MediatheqSpacing.s).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** One tonal container for every row, as on the other settings pages. */
@Composable
private fun rowColors() =
    ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
        selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

@Composable
private fun SwitchRow(
    shapes: ListItemShapes,
    title: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = shapes,
        enabled = enabled,
        colors = rowColors(),
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    ) { Text(stringResource(title)) }
}

@Composable
private fun ChoiceRow(
    shapes: ListItemShapes,
    title: Int,
    value: String,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = rowColors(),
        supportingContent = { Text(value) },
    ) { Text(stringResource(title)) }
}

@Composable
private fun ActionRow(
    shapes: ListItemShapes,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = rowColors(),
        leadingContent = { Icon(icon, contentDescription = null) },
    ) { Text(stringResource(title)) }
}

@Composable
private fun RowsRow(
    shapes: ListItemShapes,
    rows: Int,
    onChange: (Int) -> Unit,
) {
    SegmentedListItem(
        onClick = {},
        shapes = shapes,
        colors = rowColors(),
        supportingContent = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
                HomeSectionSettings.GRID_ROWS.forEach { n ->
                    FilterChip(
                        selected = n == rows,
                        onClick = { onChange(n) },
                        label = { Text(n.toString()) },
                        leadingIcon = if (n == rows) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
                    )
                }
            }
        },
    ) { Text(stringResource(R.string.home_section_rows)) }
}

/** Max items as a slider, stored once on release. */
@Composable
private fun MaxItemsRow(
    shapes: ListItemShapes,
    maxItems: Int,
    onChange: (Int) -> Unit,
) {
    var dragged by remember(maxItems) { mutableFloatStateOf(maxItems.toFloat()) }
    val range = HomeSectionSettings.MAX_ITEMS
    SegmentedListItem(
        onClick = {},
        shapes = shapes,
        colors = rowColors(),
        supportingContent = {
            Slider(
                value = dragged,
                onValueChange = { dragged = it },
                onValueChangeFinished = { onChange(dragged.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = range.last - range.first - 1,
            )
        },
        trailingContent = { Text(dragged.roundToInt().toString()) },
    ) { Text(stringResource(R.string.home_section_max)) }
}

@Composable
private fun <T> ChoiceDialog(
    title: Int,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    MqDialog(
        title = stringResource(title),
        confirmLabel = null,
        onConfirm = {},
        dismissLabel = stringResource(R.string.home_section_cancel),
        onDismiss = onDismiss,
    ) {
        Column {
            options.forEach { option ->
                ListItem(
                    modifier =
                        Modifier.selectable(option == selected, role = Role.RadioButton) {
                            onSelect(option)
                            onDismiss()
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    trailingContent = {
                        if (option == selected) Icon(MqIcons.Check, contentDescription = null)
                    },
                ) { Text(label(option)) }
            }
        }
    }
}

/** Same as Media lists / On / Off. */
@Composable
private fun TriDialog(
    title: Int,
    value: Boolean?,
    onSelect: (Boolean?) -> Unit,
    onDismiss: () -> Unit,
) = ChoiceDialog(title, listOf(null, true, false), value, { triLabel(it) }, onSelect, onDismiss)

@Composable
private fun ConfirmDialog(
    text: Int,
    confirm: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MqDialog(
        title = null,
        confirmLabel = stringResource(confirm),
        onConfirm = {
            onConfirm()
            onDismiss()
        },
        dismissLabel = stringResource(R.string.home_section_cancel),
        onDismiss = onDismiss,
    ) { Text(stringResource(text), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun displayLabel(display: HomeDisplay): String =
    stringResource(
        when (display) {
            HomeDisplay.Row -> R.string.home_display_row
            HomeDisplay.Grid -> R.string.home_display_grid
            HomeDisplay.Carousel -> R.string.home_display_carousel
            HomeDisplay.List -> R.string.home_display_list
        },
    )

@Composable
private fun triLabel(value: Boolean?): String =
    stringResource(
        when (value) {
            null -> R.string.home_section_same_as_lists
            true -> R.string.home_section_on
            false -> R.string.home_section_off
        },
    )
