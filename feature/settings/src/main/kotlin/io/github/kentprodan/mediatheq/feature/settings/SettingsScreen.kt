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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.component.rememberMqReorderState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.HomeSection

/** The Settings pages with settings of their own (#120); they share [SettingsViewModel] until a page needs its own. */
enum class SettingsPage(
    @StringRes val title: Int,
) {
    Interface(R.string.settings_interface),
    Theme(R.string.settings_theme),
    Navigation(R.string.settings_navigation),
    MediaLists(R.string.settings_media_lists),
    NowPlaying(R.string.settings_now_playing_screens),
    Home(R.string.settings_home_page),
    Library(R.string.settings_library_page),
    Album(R.string.settings_album_page),
    Artist(R.string.settings_artist_page),
    Genre(R.string.settings_genre_page),
    Playback(R.string.settings_playback),
    Storage(R.string.settings_storage_page),
    Providers(R.string.settings_providers),
}

@Composable
internal fun SettingsPageRoute(
    page: SettingsPage,
    onOpenAudio: () -> Unit,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit = {},
    onOpenNavigation: () -> Unit = {},
    onOpenMediaLists: () -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
    onOpenHomePage: () -> Unit = {},
    onOpenLibraryPage: () -> Unit = {},
    onOpenAlbumPage: () -> Unit = {},
    onOpenArtistPage: () -> Unit = {},
    onOpenGenrePage: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsPageScreen(
        page,
        state,
        viewModel::onAction,
        onOpenAudio,
        onBack,
        onOpenTheme,
        onOpenNavigation,
        onOpenMediaLists,
        onOpenNowPlaying,
        onOpenHomePage,
        onOpenLibraryPage,
        onOpenAlbumPage,
        onOpenArtistPage,
        onOpenGenrePage,
    )
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
    onOpenTheme: () -> Unit = {},
    onOpenNavigation: () -> Unit = {},
    onOpenMediaLists: () -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
    onOpenHomePage: () -> Unit = {},
    onOpenLibraryPage: () -> Unit = {},
    onOpenAlbumPage: () -> Unit = {},
    onOpenArtistPage: () -> Unit = {},
    onOpenGenrePage: () -> Unit = {},
) {
    // Survive rotation; only the Theme, Navigation, Media lists, Now playing, Home and Library pages open dialogs.
    var dialog by rememberSaveable { mutableStateOf<ThemeDialog?>(null) }
    var navigationDialog by rememberSaveable { mutableStateOf<NavigationDialog?>(null) }
    var mediaListsDialog by rememberSaveable { mutableStateOf<MediaListsDialog?>(null) }
    var nowPlayingDialog by rememberSaveable { mutableStateOf<NowPlayingDialog?>(null) }
    var homeDialog by rememberSaveable { mutableStateOf<HomePageDialog?>(null) }
    var libraryDialog by rememberSaveable { mutableStateOf<LibraryPageDialog?>(null) }
    var albumDialog by rememberSaveable { mutableStateOf<AlbumPageDialog?>(null) }
    var artistDialog by rememberSaveable { mutableStateOf<ArtistPageDialog?>(null) }
    var genreDialog by rememberSaveable { mutableStateOf<GenrePageDialog?>(null) }
    val listState = rememberLazyListState()
    // The Home page's rows as shown: a drag moves them here and saves the order once, on drop.
    val homeOrder =
        (state as? SettingsUiState.Content)
            ?.library
            ?.home
            ?.order
            .orEmpty()
    var homeRows by remember(homeOrder) { mutableStateOf(homeOrder) }
    val reorder =
        rememberMqReorderState(
            listState,
            onMove = { from, to -> homeRows = homeRows.toMutableList().apply { add(to, removeAt(from)) } },
            onDrop = { _, _ -> if (homeRows != homeOrder) onAction(SettingsAction.SetHomeOrder(homeRows)) },
            // The sections come first; the groups after them are no drop targets.
            canDropOn = { it is HomeSection },
        )
    SettingsScaffold(stringResource(page.title), onBack, listState) {
        val content = state as? SettingsUiState.Content ?: return@SettingsScaffold
        when (page) {
            SettingsPage.Interface -> {
                item(key = "appearance") {
                    AppearanceGroup(
                        content.library,
                        onAction,
                        onOpenTheme,
                        onOpenNavigation,
                        onOpenMediaLists,
                        onOpenNowPlaying,
                    )
                }
                item(key = "pages") {
                    Group(R.string.settings_page_settings) {
                        item { shapes ->
                            SegmentedListItem(
                                onClick = onOpenHomePage,
                                shapes = shapes,
                                colors = settingsColors(),
                                supportingContent = { Text(stringResource(R.string.settings_home_page_summary)) },
                                trailingContent = { Icon(MqIcons.Home, contentDescription = null) },
                            ) { Text(stringResource(R.string.settings_home_page)) }
                        }
                        item { shapes ->
                            SegmentedListItem(
                                onClick = onOpenLibraryPage,
                                shapes = shapes,
                                colors = settingsColors(),
                                supportingContent = { Text(stringResource(R.string.settings_library_page_summary)) },
                                trailingContent = { Icon(MqIcons.Library, contentDescription = null) },
                            ) { Text(stringResource(R.string.settings_library_page)) }
                        }
                        item { shapes ->
                            SegmentedListItem(
                                onClick = onOpenAlbumPage,
                                shapes = shapes,
                                colors = settingsColors(),
                                supportingContent = { Text(stringResource(R.string.settings_album_page_summary)) },
                                trailingContent = { Icon(MqIcons.MusicNote, contentDescription = null) },
                            ) { Text(stringResource(R.string.settings_album_page)) }
                        }
                        item { shapes ->
                            SegmentedListItem(
                                onClick = onOpenArtistPage,
                                shapes = shapes,
                                colors = settingsColors(),
                                supportingContent = { Text(stringResource(R.string.settings_artist_page_summary)) },
                                trailingContent = { Icon(MqIcons.Person, contentDescription = null) },
                            ) { Text(stringResource(R.string.settings_artist_page)) }
                        }
                        item { shapes ->
                            SegmentedListItem(
                                onClick = onOpenGenrePage,
                                shapes = shapes,
                                colors = settingsColors(),
                                supportingContent = { Text(stringResource(R.string.settings_genre_page_summary)) },
                                trailingContent = { Icon(MqIcons.Genres, contentDescription = null) },
                            ) { Text(stringResource(R.string.settings_genre_page)) }
                        }
                    }
                }
                item(key = "ratings") { RatingsGroup(content.library.ratingMode, onAction) }
            }

            SettingsPage.Theme -> {
                themeGroups(content.library.theme, onAction) { dialog = it }
            }

            SettingsPage.Navigation -> {
                navigationGroups(content.library.navigation, onAction) { navigationDialog = it }
            }

            SettingsPage.MediaLists -> {
                mediaListsGroups(content.library.mediaLists, onAction) { mediaListsDialog = it }
            }

            SettingsPage.NowPlaying -> {
                nowPlayingGroups(content.library.nowPlaying, content.library.ratingMode, onAction) {
                    nowPlayingDialog = it
                }
            }

            SettingsPage.Home -> {
                homePageGroups(content.library.home, homeRows, reorder, onAction) { homeDialog = it }
            }

            SettingsPage.Library -> {
                libraryPageGroups(content.library.libraryPage, onAction) { libraryDialog = it }
            }

            SettingsPage.Album -> {
                albumPageGroups(content.library.albumPage, onAction) { albumDialog = it }
            }

            SettingsPage.Artist -> {
                artistPageGroups(content.library.artistPage, onAction) { artistDialog = it }
            }

            SettingsPage.Genre -> {
                genrePageGroups(content.library.genrePage, onAction) { genreDialog = it }
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
    val content = state as? SettingsUiState.Content
    if (content != null) {
        dialog?.let { ThemeDialogHost(it, content.library.theme, onAction) { dialog = null } }
        navigationDialog?.let {
            NavigationDialogHost(it, content.library.navigation, onAction) { navigationDialog = null }
        }
        mediaListsDialog?.let {
            MediaListsDialogHost(it, content.library.mediaLists, onAction) { mediaListsDialog = null }
        }
        nowPlayingDialog?.let {
            NowPlayingDialogHost(it, content.library.nowPlaying, onAction) { nowPlayingDialog = null }
        }
        homeDialog?.let { HomePageDialogHost(it, onAction) { homeDialog = null } }
        libraryDialog?.let {
            LibraryPageDialogHost(it, content.library.libraryPage, onAction) { libraryDialog = null }
        }
        if (page == SettingsPage.Navigation) content.entriesDraft?.let { NavEntriesDialog(it, onAction) }
        if (page == SettingsPage.Library) content.shortcutsDraft?.let { LibraryShortcutsDialog(it, onAction) }
        albumDialog?.let {
            AlbumPageDialogHost(it, content.library.albumPage, onAction) { albumDialog = null }
        }
        if (page == SettingsPage.Album) content.sectionsDraft?.let { AlbumSectionsDialog(it, onAction) }
        artistDialog?.let {
            ArtistPageDialogHost(it, content.library.artistPage, onAction) { artistDialog = null }
        }
        if (page == SettingsPage.Artist) content.artistSectionsDraft?.let { ArtistSectionsDialog(it, onAction) }
        genreDialog?.let {
            GenrePageDialogHost(it, content.library.genrePage, onAction) { genreDialog = null }
        }
        if (page == SettingsPage.Genre) content.genreSectionsDraft?.let { GenreSectionsDialog(it, onAction) }
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
    listState: LazyListState = rememberLazyListState(),
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
            state = listState,
            contentPadding =
                PaddingValues(
                    start = MediatheqSpacing.l,
                    end = MediatheqSpacing.l,
                    top = padding.calculateTopPadding(),
                    bottom = MediatheqSpacing.l,
                ).plusBottomBars(),
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
    onOpenTheme: () -> Unit,
    onOpenNavigation: () -> Unit = {},
    onOpenMediaLists: () -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
) {
    Group(R.string.settings_appearance) {
        item { shapes ->
            SegmentedListItem(
                onClick = onOpenTheme,
                shapes = shapes,
                colors = settingsColors(),
                supportingContent = { Text(stringResource(R.string.settings_theme_summary)) },
                trailingContent = { Icon(MqIcons.Palette, contentDescription = null) },
            ) { Text(stringResource(R.string.settings_theme)) }
        }
        item { shapes ->
            SegmentedListItem(
                onClick = onOpenNavigation,
                shapes = shapes,
                colors = settingsColors(),
                supportingContent = { Text(stringResource(R.string.settings_navigation_summary)) },
                trailingContent = { Icon(MqIcons.TouchApp, contentDescription = null) },
            ) { Text(stringResource(R.string.settings_navigation)) }
        }
        item { shapes ->
            SegmentedListItem(
                onClick = onOpenMediaLists,
                shapes = shapes,
                colors = settingsColors(),
                supportingContent = { Text(stringResource(R.string.settings_media_lists_summary)) },
                trailingContent = { Icon(MqIcons.QueueMusic, contentDescription = null) },
            ) { Text(stringResource(R.string.settings_media_lists)) }
        }
        item { shapes ->
            SegmentedListItem(
                onClick = onOpenNowPlaying,
                shapes = shapes,
                colors = settingsColors(),
                supportingContent = { Text(stringResource(R.string.settings_now_playing_summary)) },
                trailingContent = { Icon(MqIcons.PlayArrow, contentDescription = null) },
            ) { Text(stringResource(R.string.settings_now_playing_screens)) }
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
internal fun SwitchItem(
    shapes: ListItemShapes,
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = shapes,
        enabled = enabled,
        colors = settingsColors(),
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    ) { Text(title) }
}

/** A choice of a few values: the chips under the title, the selected one checked. */
@Composable
internal fun <T> ChoiceItem(
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
