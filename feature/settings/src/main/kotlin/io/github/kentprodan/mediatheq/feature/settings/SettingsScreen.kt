package io.github.kentprodan.mediatheq.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** The Settings pages with settings of their own (#120); they share [SettingsViewModel] until a page needs its own. */
enum class SettingsPage(
    @StringRes val title: Int,
) {
    Interface(R.string.settings_interface),
    Playback(R.string.settings_playback),
    Storage(R.string.settings_storage_page),
    Providers(R.string.settings_providers),
}

@Composable
internal fun SettingsPageRoute(
    page: SettingsPage,
    onOpenAudio: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsPageScreen(page, state, viewModel::onAction, onOpenAudio, onBack)
}

/**
 * One Settings page: its groups of segmented list items (expressive-design, component map). Choices are chips under
 * their title, on/off settings are whole-row switches; everything applies at once.
 */
@Composable
fun SettingsPageScreen(
    page: SettingsPage,
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenAudio: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(page.title), onBack) {
        val content = state as? SettingsUiState.Content ?: return@SettingsScaffold
        when (page) {
            SettingsPage.Interface -> {
                item(key = "appearance") { AppearanceGroup(content.library, onAction) }
                item(key = "ratings") { RatingsGroup(content.library.ratingMode, onAction) }
            }

            SettingsPage.Playback -> {
                item(key = "playback") { PlaybackGroup(content.audio, onAction, onOpenAudio) }
            }

            SettingsPage.Storage -> {
                item(key = "storage") { StorageGroup(content.audio, content.library, onAction) }
            }

            SettingsPage.Providers -> {
                item(key = "account") { AccountGroup(content, onAction) }
            }
        }
    }
}

/** Settings -> About: the version and the app's own pages; needs no settings. */
@Composable
fun AboutSettingsScreen(
    versionName: String,
    onOpenShowcase: () -> Unit,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(R.string.settings_about), onBack) {
        item(key = "about") { AboutGroup(versionName, onOpenShowcase, onOpenLicenses) }
    }
}

/** The frame of the hub and every Settings page: top bar with back, then the groups. */
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    groups: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
        // The app shell already pads for the navigation bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = MediatheqSpacing.l,
                    end = MediatheqSpacing.l,
                    top = padding.calculateTopPadding(),
                    bottom = MediatheqSpacing.l,
                ),
            verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
            content = groups,
        )
    }
}

@Composable
internal fun AccountGroup(
    content: SettingsUiState.Content,
    onAction: (SettingsAction) -> Unit,
) {
    Group(R.string.settings_account) {
        item { shapes -> InfoItem(shapes, stringResource(R.string.settings_server), content.serverName) }
        item { shapes -> InfoItem(shapes, stringResource(R.string.settings_user), content.userName) }
        item { shapes ->
            SegmentedListItem(
                onClick = { onAction(SettingsAction.LogOut) },
                shapes = shapes,
                colors = settingsColors(),
            ) {
                Text(stringResource(R.string.settings_log_out), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun PlaybackGroup(
    audio: AudioPreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenAudio: () -> Unit,
) {
    Group(R.string.settings_playback) {
        listOf(false to R.string.settings_quality_unmetered, true to R.string.settings_quality_metered)
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
            SegmentedListItem(
                onClick = onOpenAudio,
                shapes = shapes,
                colors = settingsColors(),
                supportingContent = { Text(stringResource(R.string.settings_audio_summary)) },
                trailingContent = { Icon(MqIcons.Tune, contentDescription = null) },
            ) { Text(stringResource(R.string.settings_audio)) }
        }
    }
}

@Composable
internal fun StorageGroup(
    audio: AudioPreferences,
    library: LibraryPreferences,
    onAction: (SettingsAction) -> Unit,
) {
    Group(R.string.settings_storage) {
        item { shapes ->
            SwitchItem(
                shapes,
                stringResource(R.string.settings_offline_mode),
                stringResource(R.string.settings_offline_mode_summary),
                checked = library.offlineMode,
                onCheckedChange = { onAction(SettingsAction.SetOfflineMode(it)) },
            )
        }
        item { shapes ->
            SwitchItem(
                shapes,
                stringResource(R.string.settings_downloads_unmetered),
                stringResource(R.string.settings_downloads_unmetered_summary),
                checked = audio.downloadsUnmeteredOnly,
                onCheckedChange = { onAction(SettingsAction.SetDownloadsUnmeteredOnly(it)) },
            )
        }
        item { shapes ->
            ChoiceItem(
                shapes,
                stringResource(R.string.settings_cache),
                stringResource(R.string.settings_cache_summary),
                AudioPreferences.CACHE_SIZES_GB,
                selected = audio.streamingCacheGb,
                label = { stringResource(R.string.settings_cache_gb, it) },
                onSelect = { onAction(SettingsAction.SetStreamingCacheGb(it)) },
            )
        }
    }
}

@Composable
internal fun AppearanceGroup(
    library: LibraryPreferences,
    onAction: (SettingsAction) -> Unit,
) {
    Group(R.string.settings_appearance) {
        item { shapes ->
            SwitchItem(
                shapes,
                stringResource(R.string.settings_dynamic_color),
                stringResource(R.string.settings_dynamic_color_summary),
                checked = library.dynamicColor,
                onCheckedChange = { onAction(SettingsAction.SetDynamicColor(it)) },
            )
        }
        item { shapes ->
            ChoiceItem(
                shapes,
                stringResource(R.string.settings_album_columns),
                summary = null,
                LibraryPreferences.ALBUM_COLUMNS.toList(),
                selected = library.albumColumns,
                label = { it.toString() },
                onSelect = { onAction(SettingsAction.SetAlbumColumns(it)) },
            )
        }
    }
}

@Composable
internal fun RatingsGroup(
    mode: RatingMode,
    onAction: (SettingsAction) -> Unit,
) {
    Group(R.string.settings_ratings) {
        item { shapes ->
            ChoiceItem(
                shapes,
                stringResource(R.string.settings_rating_mode),
                stringResource(R.string.settings_rating_mode_summary),
                RatingMode.entries,
                selected = mode,
                label = { stringResource(it.label) },
                onSelect = { onAction(SettingsAction.SetRatingMode(it)) },
            )
        }
    }
}

@Composable
internal fun AboutGroup(
    versionName: String,
    onOpenShowcase: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    Group(R.string.settings_about) {
        item { shapes -> InfoItem(shapes, stringResource(R.string.settings_version), versionName) }
        item { shapes ->
            SegmentedListItem(
                onClick = onOpenLicenses,
                shapes = shapes,
                colors = settingsColors(),
                supportingContent = { Text(stringResource(R.string.settings_license_summary)) },
            ) { Text(stringResource(R.string.settings_license)) }
        }
        item { shapes ->
            SegmentedListItem(
                onClick = onOpenShowcase,
                shapes = shapes,
                colors = settingsColors(),
                supportingContent = { Text(stringResource(R.string.settings_showcase_summary)) },
            ) { Text(stringResource(R.string.settings_showcase)) }
        }
    }
}

/**
 * One tonal container for every row; a switched-on row keeps it too (the switch shows the state), so the groups read
 * as cards on the screen's surface.
 */
@Composable
internal fun settingsColors() =
    ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
        selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

/** A titled group: its items share the segmented shapes (rounded ends, small gaps). */
@Composable
internal fun Group(
    title: Int,
    items: GroupScope.() -> Unit,
) {
    val rows = GroupScope().apply(items).rows
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = MediatheqSpacing.l, bottom = MediatheqSpacing.s).semantics { heading() },
        )
        rows.forEachIndexed { index, row -> row(ListItemDefaults.segmentedShapes(index, rows.size)) }
    }
}

internal class GroupScope {
    val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    fun item(row: @Composable (ListItemShapes) -> Unit) {
        rows += row
    }
}

@Composable
private fun InfoItem(
    shapes: ListItemShapes,
    title: String,
    value: String,
) {
    SegmentedListItem(
        shapes = shapes,
        colors = settingsColors(),
        supportingContent = { Text(value) },
    ) { Text(title) }
}

/** A whole-row switch: TalkBack reads title, summary and state as one control. */
@Composable
private fun SwitchItem(
    shapes: ListItemShapes,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = shapes,
        colors = settingsColors(),
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) { Text(title) }
}

/** A choice of a few values: the chips under the title, the selected one checked. */
@Composable
private fun <T> ChoiceItem(
    shapes: ListItemShapes,
    title: String,
    summary: String?,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    SegmentedListItem(
        shapes = shapes,
        colors = settingsColors(),
        supportingContent = {
            Column {
                summary?.let { Text(it) }
                FlowRow(
                    Modifier.padding(top = MediatheqSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
                ) {
                    options.forEach { option ->
                        FilterChip(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                            label = { Text(label(option)) },
                            leadingIcon =
                                if (option == selected) {
                                    { Icon(MqIcons.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }
        },
    ) { Text(title) }
}

@Composable
private fun StreamingQuality.label(): String =
    bitrate?.let { stringResource(R.string.settings_quality_kbps, it / 1000) }
        ?: stringResource(R.string.settings_quality_original)

private val Normalization.label: Int
    get() =
        when (this) {
            Normalization.Album -> R.string.settings_normalization_album
            Normalization.Track -> R.string.settings_normalization_track
            Normalization.Off -> R.string.settings_normalization_off
        }

private val RatingMode.label: Int
    get() =
        when (this) {
            RatingMode.WholeStars -> R.string.settings_rating_whole
            RatingMode.HalfStars -> R.string.settings_rating_half
            RatingMode.Off -> R.string.settings_rating_off
        }
