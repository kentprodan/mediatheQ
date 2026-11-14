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
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.kentprodan.mediatheq.core.data.VideoPreferences
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.component.rememberMqReorderState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.HomeSection

/** The Settings pages with settings of their own (#120); they share [SettingsViewModel] until a page needs its own. */
enum class SettingsPage(
    @StringRes val title: Int,
) {
    Interface(R.string.settings_interface),
    AppMode(R.string.settings_app_mode),
    Theme(R.string.settings_theme),
    Navigation(R.string.settings_navigation),
    MediaLists(R.string.settings_media_lists),
    NowPlaying(R.string.settings_now_playing_screens),
    Home(R.string.settings_home_page),
    Library(R.string.settings_library_page),
    Album(R.string.settings_album_page),
    Artist(R.string.settings_artist_page),
    Genre(R.string.settings_genre_page),
    Playlist(R.string.settings_playlist_page),
    Search(R.string.settings_search_page),
    Advanced(R.string.settings_advanced),
    Playback(R.string.settings_playback),
    PlaybackBehavior(R.string.settings_pb_behavior),
    Decoding(R.string.settings_decoding),
    Video(R.string.settings_video),
    AudioFocus(R.string.settings_audio_focus),
    Headset(R.string.settings_headset),
    NotificationButtons(R.string.settings_notification_buttons),
    Transitions(R.string.settings_transitions),
    AutoActions(R.string.settings_auto_actions),
    Storage(R.string.settings_storage_page),
    StorageGeneral(R.string.settings_st_general),
    StorageMedia(R.string.settings_st_media),
    StoragePlayback(R.string.settings_st_playback),
    StorageImages(R.string.settings_st_images),
}

@Composable
internal fun SettingsPageRoute(
    page: SettingsPage,
    onOpenOutputs: () -> Unit,
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
    onOpenPlaylistPage: () -> Unit = {},
    onOpenSearchPage: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {},
    onOpenBehavior: () -> Unit = {},
    onOpenDecoding: () -> Unit = {},
    onOpenAudioFocus: () -> Unit = {},
    onOpenHeadset: () -> Unit = {},
    onOpenNotificationButtons: () -> Unit = {},
    onOpenAutoActions: () -> Unit = {},
    onOpenTransitions: () -> Unit = {},
    onOpenStoragePage: (SettingsPage) -> Unit = {},
    onOpenHomeSection: (HomeSection) -> Unit = {},
    onOpenAppMode: () -> Unit = {},
    onOpenVideo: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsPageScreen(
        page,
        state,
        viewModel::onAction,
        onOpenOutputs,
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
        onOpenPlaylistPage,
        onOpenSearchPage,
        onOpenAdvanced,
        onOpenBehavior,
        onOpenDecoding,
        onOpenAudioFocus,
        onOpenHeadset,
        onOpenNotificationButtons,
        onOpenAutoActions,
        onOpenTransitions,
        onOpenStoragePage,
        onOpenHomeSection,
        onOpenAppMode,
        onOpenVideo,
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
    onOpenOutputs: () -> Unit,
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
    onOpenPlaylistPage: () -> Unit = {},
    onOpenSearchPage: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {},
    onOpenBehavior: () -> Unit = {},
    onOpenDecoding: () -> Unit = {},
    onOpenAudioFocus: () -> Unit = {},
    onOpenHeadset: () -> Unit = {},
    onOpenNotificationButtons: () -> Unit = {},
    onOpenAutoActions: () -> Unit = {},
    onOpenTransitions: () -> Unit = {},
    onOpenStoragePage: (SettingsPage) -> Unit = {},
    onOpenHomeSection: (HomeSection) -> Unit = {},
    onOpenAppMode: () -> Unit = {},
    onOpenVideo: () -> Unit = {},
) {
    var storageDialog by rememberSaveable { mutableStateOf<StorageDialog?>(null) }
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
    var playlistDialog by rememberSaveable { mutableStateOf<PlaylistPageDialog?>(null) }
    var searchDialog by rememberSaveable { mutableStateOf<SearchPageDialog?>(null) }
    var advancedDialog by rememberSaveable { mutableStateOf<AdvancedDialog?>(null) }
    // Restore defaults of the playback pages (#138, #139), and their choice dialogs.
    var restoring by rememberSaveable { mutableStateOf(false) }
    var focusChoice by rememberSaveable { mutableStateOf(false) }
    var headsetDialog by rememberSaveable { mutableStateOf<HeadsetDialog?>(null) }
    var actionDialog by rememberSaveable { mutableStateOf<NotificationActionDialog?>(null) }
    val listState = rememberLazyListState()
    // The Home page's rows as shown: a drag moves them here and saves the order once, on drop.
    val fullHomeOrder =
        (state as? SettingsUiState.Content)
            ?.library
            ?.home
            ?.order
            .orEmpty()
    // Only the sections of the app mode (ADR 0051): the others keep their place after them when the order is saved.
    val mode = (state as? SettingsUiState.Content)?.appMode ?: AppMode.Music
    val homeOrder = fullHomeOrder.filter { it.allowed(mode) }
    var homeRows by remember(homeOrder) { mutableStateOf(homeOrder) }
    val reorder =
        rememberMqReorderState(
            listState,
            onMove = { from, to -> homeRows = homeRows.toMutableList().apply { add(to, removeAt(from)) } },
            onDrop = { _, _ ->
                if (homeRows != homeOrder) {
                    onAction(SettingsAction.SetHomeOrder(homeRows + fullHomeOrder.filterNot { it.allowed(mode) }))
                }
            },
            // The sections come first; the groups after them are no drop targets.
            canDropOn = { it is HomeSection },
        )
    SettingsScaffold(stringResource(page.title), onBack, listState) {
        val content = state as? SettingsUiState.Content ?: return@SettingsScaffold
        val music = content.appMode.music
        when (page) {
            SettingsPage.Interface -> {
                if (content.videoAvailable) {
                    item(key = "app_mode") { AppModeGroup(content.appMode, onOpenAppMode) }
                }
                item(key = "appearance") {
                    AppearanceGroup(
                        content.library,
                        onAction,
                        onOpenTheme,
                        onOpenNavigation,
                        onOpenMediaLists,
                        onOpenNowPlaying.takeIf { music },
                    )
                }
                item(key = "pages") {
                    Group(R.string.settings_page_settings) {
                        pageRow(
                            R.string.settings_home_page,
                            R.string.settings_home_page_summary,
                            { MqIcons.Home },
                            onOpenHomePage,
                        )
                        pageRow(
                            R.string.settings_library_page,
                            R.string.settings_library_page_summary,
                            { MqIcons.Library },
                            onOpenLibraryPage,
                        )
                        // Music-only pages: hidden in Video mode (ADR 0051).
                        if (music) {
                            pageRow(
                                R.string.settings_album_page,
                                R.string.settings_album_page_summary,
                                { MqIcons.MusicNote },
                                onOpenAlbumPage,
                            )
                            pageRow(
                                R.string.settings_artist_page,
                                R.string.settings_artist_page_summary,
                                { MqIcons.Person },
                                onOpenArtistPage,
                            )
                            pageRow(
                                R.string.settings_genre_page,
                                R.string.settings_genre_page_summary,
                                { MqIcons.Genres },
                                onOpenGenrePage,
                            )
                            pageRow(
                                R.string.settings_playlist_page,
                                R.string.settings_playlist_page_summary,
                                { MqIcons.QueueMusic },
                                onOpenPlaylistPage,
                            )
                        }
                        pageRow(
                            R.string.settings_search_page,
                            R.string.settings_search_page_summary,
                            { MqIcons.Search },
                            onOpenSearchPage,
                        )
                    }
                }
                item(key = "ratings") { RatingsGroup(content.library.ratingMode, onAction) }
                item(key = "advanced") {
                    Group(R.string.settings_advanced) {
                        item { shapes ->
                            SegmentedListItem(
                                onClick = onOpenAdvanced,
                                shapes = shapes,
                                colors = settingsColors(),
                                supportingContent = { Text(stringResource(R.string.settings_advanced_summary)) },
                                trailingContent = { Icon(MqIcons.Tune, contentDescription = null) },
                            ) { Text(stringResource(R.string.settings_advanced_settings)) }
                        }
                    }
                }
            }

            SettingsPage.AppMode -> {
                appModeGroups(content.appMode, onAction)
            }

            SettingsPage.Theme -> {
                themeGroups(content.library.theme, onAction) { dialog = it }
            }

            SettingsPage.Navigation -> {
                navigationGroups(content.library.navigation, content.appMode, onAction) { navigationDialog = it }
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
                homePageGroups(content.library.home, homeRows, reorder, onAction, onOpenHomeSection) { homeDialog = it }
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

            SettingsPage.Playlist -> {
                playlistPageGroups(content.library.playlistPage) { playlistDialog = it }
            }

            SettingsPage.Search -> {
                searchPageGroups(content.library.searchPage, onAction) { searchDialog = it }
            }

            SettingsPage.Advanced -> {
                advancedGroups(content.library.advanced, onAction) { advancedDialog = it }
            }

            SettingsPage.Playback -> {
                playbackHubGroups(
                    PlaybackPages(
                        onOpenBehavior,
                        onOpenDecoding,
                        onOpenAudioFocus,
                        onOpenHeadset,
                        onOpenNotificationButtons,
                        onOpenOutputs,
                        onOpenAutoActions,
                        onOpenTransitions,
                        music,
                        video = onOpenVideo,
                        showVideo = content.appMode.video,
                    ),
                )
            }

            SettingsPage.Video -> {
                videoGroups(content.video, onAction) { restoring = true }
            }

            SettingsPage.PlaybackBehavior -> {
                behaviorGroups(content.audio.behavior, music, onAction) { restoring = true }
            }

            SettingsPage.Decoding -> {
                decodingGroups(content.audio, onAction) { restoring = true }
            }

            SettingsPage.AudioFocus -> {
                audioFocusGroups(content.audio.audioFocus, onAction, { focusChoice = true }) { restoring = true }
            }

            SettingsPage.Headset -> {
                headsetGroups(content.audio.headset, onAction, { headsetDialog = it }) { restoring = true }
            }

            SettingsPage.NotificationButtons -> {
                notificationButtonsGroups(content.audio.notificationButtons, onAction, { actionDialog = it }) {
                    restoring = true
                }
            }

            SettingsPage.Transitions -> {
                transitionsGroups(content.audio.transitions, onAction) { restoring = true }
            }

            SettingsPage.AutoActions -> {
                autoActionsGroups(content.audio.autoActions, onAction) { restoring = true }
            }

            SettingsPage.Storage -> {
                storageHubGroups(onOpenStoragePage)
            }

            SettingsPage.StorageGeneral -> {
                storageGeneralGroups(content.library, content.storageLocations, onAction, { storageDialog = it }) {
                    restoring = true
                }
            }

            SettingsPage.StorageMedia -> {
                storageMediaGroups(content.audio, onAction, { storageDialog = it }) { restoring = true }
            }

            SettingsPage.StoragePlayback -> {
                storagePlaybackGroups(content.audio, onAction) { restoring = true }
            }

            SettingsPage.StorageImages -> {
                storageImagesGroups(content.library.images, onAction) { restoring = true }
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
        playlistDialog?.let {
            PlaylistPageDialogHost(it, content.library.playlistPage, onAction) { playlistDialog = null }
        }
        searchDialog?.let {
            SearchPageDialogHost(it, content.library.searchPage, onAction) { searchDialog = null }
        }
        advancedDialog?.let {
            AdvancedDialogHost(it, content.library.advanced, onAction) { advancedDialog = null }
        }
        if (restoring) {
            val restore =
                when (page) {
                    SettingsPage.Decoding -> {
                        SettingsAction.RestoreDecodingDefaults
                    }

                    SettingsPage.Video -> {
                        SettingsAction.SetVideo(VideoPreferences())
                    }

                    SettingsPage.AudioFocus -> {
                        SettingsAction.SetAudioFocus(null)
                    }

                    SettingsPage.Headset -> {
                        SettingsAction.SetHeadset(null)
                    }

                    SettingsPage.NotificationButtons -> {
                        SettingsAction.SetNotificationButtons(null)
                    }

                    SettingsPage.AutoActions -> {
                        SettingsAction.SetAutoActions(null)
                    }

                    SettingsPage.Transitions -> {
                        SettingsAction.SetTransitions(null)
                    }

                    SettingsPage.StorageGeneral -> {
                        SettingsAction.RestoreOfflineDefaults
                    }

                    SettingsPage.StorageMedia -> {
                        SettingsAction.RestoreDownloadDefaults
                    }

                    SettingsPage.StoragePlayback -> {
                        SettingsAction.SetStreamingCacheGb(
                            AudioPreferences.DEFAULT_CACHE_GB,
                        )
                    }

                    SettingsPage.StorageImages -> {
                        SettingsAction.SetImages(null)
                    }

                    else -> {
                        SettingsAction.RestoreBehaviorDefaults
                    }
                }
            RestoreDefaultsDialog({ onAction(restore) }) { restoring = false }
        }
        if (focusChoice) ShortFocusDialog(content.audio.audioFocus, onAction) { focusChoice = false }
        storageDialog?.let {
            StorageDialogHost(it, content.audio, content.library, content.storageLocations, onAction) {
                storageDialog = null
            }
        }
        MoveDownloadsDialog(content.storageMove) { onAction(SettingsAction.DismissMoveDownloads) }
        headsetDialog?.let { HeadsetDialogHost(it, content.audio.headset, onAction) { headsetDialog = null } }
        actionDialog?.let {
            NotificationActionDialogHost(it, content.audio.notificationButtons, onAction) { actionDialog = null }
        }
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
internal fun AppearanceGroup(
    library: LibraryPreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenTheme: () -> Unit,
    onOpenNavigation: () -> Unit = {},
    onOpenMediaLists: () -> Unit = {},
    // `null` hides the row: Now playing screens are music only (ADR 0051).
    onOpenNowPlaying: (() -> Unit)? = {},
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
        if (onOpenNowPlaying != null) {
            item { shapes ->
                SegmentedListItem(
                    onClick = onOpenNowPlaying,
                    shapes = shapes,
                    colors = settingsColors(),
                    supportingContent = { Text(stringResource(R.string.settings_now_playing_summary)) },
                    trailingContent = { Icon(MqIcons.PlayArrow, contentDescription = null) },
                ) { Text(stringResource(R.string.settings_now_playing_screens)) }
            }
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

/** A row of Settings -> Interface -> Page settings that opens its page. */
internal fun GroupScope.pageRow(
    @StringRes title: Int,
    @StringRes summary: Int,
    icon: @Composable () -> ImageVector,
    onClick: () -> Unit,
) = item { shapes ->
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = settingsColors(),
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Icon(icon(), contentDescription = null) },
    ) { Text(stringResource(title)) }
}

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
    enabled: Boolean = true,
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
                            enabled = enabled,
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
internal fun StreamingQuality.label(): String =
    bitrate?.let { stringResource(R.string.settings_quality_kbps, it / 1000) }
        ?: stringResource(R.string.settings_quality_original)

internal val Normalization.label: Int
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
