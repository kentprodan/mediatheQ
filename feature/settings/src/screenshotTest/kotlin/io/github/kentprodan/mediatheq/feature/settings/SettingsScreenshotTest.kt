package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.AdvancedPreferences
import io.github.kentprodan.mediatheq.core.data.AlbumButton
import io.github.kentprodan.mediatheq.core.data.AlbumPagePreferences
import io.github.kentprodan.mediatheq.core.data.AlbumSection
import io.github.kentprodan.mediatheq.core.data.AppFont
import io.github.kentprodan.mediatheq.core.data.ArtistButton
import io.github.kentprodan.mediatheq.core.data.ArtistPagePreferences
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.ColorSource
import io.github.kentprodan.mediatheq.core.data.CoverTap
import io.github.kentprodan.mediatheq.core.data.GenreButton
import io.github.kentprodan.mediatheq.core.data.GenrePagePreferences
import io.github.kentprodan.mediatheq.core.data.GenreSection
import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.MainNavEntry
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.MiniProgress
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.NowPlayingPreferences
import io.github.kentprodan.mediatheq.core.data.PlayerBackground
import io.github.kentprodan.mediatheq.core.data.PlayerProgress
import io.github.kentprodan.mediatheq.core.data.PlaylistButton
import io.github.kentprodan.mediatheq.core.data.PlaylistPagePreferences
import io.github.kentprodan.mediatheq.core.data.PortraitNavStyle
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.SearchPagePreferences
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SwipeAction
import io.github.kentprodan.mediatheq.core.data.ThemeMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.TopButtonStyle
import io.github.kentprodan.mediatheq.core.data.TrackAction
import io.github.kentprodan.mediatheq.core.data.TrackInfo
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqColorSource
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqSeedPresets
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeMode
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeOptions
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.ui.label

private val content =
    SettingsUiState.Content(
        "Home server",
        "Ana",
        AudioPreferences(meteredQuality = StreamingQuality.Kbps128),
        LibraryPreferences(albumColumns = 3, ratingMode = RatingMode.HalfStars),
    )

/** The hub: Settings, Miscellaneous and About groups. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsHubScreenshot() {
    MediatheqTheme { SettingsHubScreen(onOpen = {}, onBack = {}) }
}

@Composable
private fun Page(page: SettingsPage) {
    MediatheqTheme { SettingsPageScreen(page, content, {}, {}, {}) }
}

/** Interface: colors, album grid, ratings. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsInterfaceScreenshot() = Page(SettingsPage.Interface)

/** Playback hub (#138): Playback, Decoding and transcoding, normalization, the Audio page. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsPlaybackScreenshot() = Page(SettingsPage.Playback)

/** Settings -> Playback -> Decoding and transcoding (#138), defaults. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsDecodingScreenshot() = Page(SettingsPage.Decoding)

/** Settings -> Playback -> Playback (#138), defaults. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsPlaybackBehaviorScreenshot() = Page(SettingsPage.PlaybackBehavior)

/** Offline, cache, and download. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsStorageScreenshot() = Page(SettingsPage.Storage)

/** Manage media providers: the account and Log out. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsProvidersScreenshot() = Page(SettingsPage.Providers)

/** About: version, licenses, showcase. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsAboutScreenshot() {
    MediatheqTheme { AboutSettingsScreen("0.1.0", {}, {}, {}) }
}

@Composable
private fun ThemePage(
    theme: ThemePreferences = ThemePreferences(),
    options: MqThemeOptions = MqThemeOptions(),
) {
    MediatheqTheme(options) {
        SettingsPageScreen(SettingsPage.Theme, content.copy(library = content.library.copy(theme = theme)), {}, {}, {})
    }
}

/** Theme, font and colors: Iris, system mode. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsThemeScreenshot() = ThemePage()

/** Theme, font and colors with a custom color: presets, hue slider, the generated scheme's swatches. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsThemeCustomScreenshot() {
    val teal = MqSeedPresets[2]
    ThemePage(
        ThemePreferences(colorSource = ColorSource.Custom, customSeed = teal),
        MqThemeOptions(colorSource = MqColorSource.Custom, customSeed = teal),
    )
}

/** Theme, font and colors in Black mode. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsThemeBlackScreenshot() =
    ThemePage(ThemePreferences(mode = ThemeMode.Black), MqThemeOptions(mode = MqThemeMode.Black))

/** The Now playing color source dialog's content, Cover selected. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsNowPlayingColorsDialogScreenshot() {
    MediatheqTheme { ThemeChoiceDialogContent(ThemeDialog.NowPlayingColors, ThemePreferences()) }
}

/** The typography dialog's content, Lato selected (fonts show in the system font: layoutlib cannot fetch them). */
@PreviewTest
@MqPreviews
@Composable
fun SettingsTypographyDialogScreenshot() {
    MediatheqTheme { ThemeChoiceDialogContent(ThemeDialog.Font, ThemePreferences(font = AppFont.Lato)) }
}

/** The hub with Downloads out of the navigation: "Manage offline files" shows (#122). */
@PreviewTest
@MqPreviews
@Composable
fun SettingsHubManageOfflineFilesScreenshot() {
    MediatheqTheme { SettingsHubScreen(onOpen = {}, onBack = {}, showsManageOfflineFiles = true) }
}

/** Navigation and media start with some choices made: six entries, floating toolbar + FAB, 50 % transparency. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsNavigationScreenshot() {
    val navigation =
        NavigationPreferences(
            entries =
                listOf(
                    MainNavEntry.Home,
                    MainNavEntry.Library,
                    MainNavEntry.Albums,
                    MainNavEntry.Tracks,
                    MainNavEntry.Downloads,
                    MainNavEntry.Search,
                ),
            portraitStyle = PortraitNavStyle.FloatingToolbarWithFab,
            transparencyPercent = 50,
            trackAction = TrackAction.PlayNext,
        )
    MediatheqTheme {
        SettingsPageScreen(
            SettingsPage.Navigation,
            content.copy(
                library = content.library.copy(navigation = navigation),
            ),
            {
            },
            {},
            {},
        )
    }
}

/** "Organize your navigation": the default four checked first, the rest below. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsNavEntriesDialogScreenshot() {
    MediatheqTheme { NavEntriesDialogContent(navEntriesDraft(NavigationPreferences.DEFAULT_ENTRIES)) }
}

/** Six entries checked: the others cannot be checked. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsNavEntriesDialogFullScreenshot() {
    val six =
        listOf(
            MainNavEntry.Home,
            MainNavEntry.Tracks,
            MainNavEntry.Albums,
            MainNavEntry.AlbumArtists,
            MainNavEntry.Playlists,
            MainNavEntry.Search,
        )
    MediatheqTheme { NavEntriesDialogContent(navEntriesDraft(six)) }
}

/** The default track action dialog's content, Play selected. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsTrackActionDialogScreenshot() {
    MediatheqTheme { NavigationChoiceDialogContent(NavigationDialog.TrackAction, NavigationPreferences()) }
}

/** The portrait navigation style dialog's content, Bottom bar selected. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsPortraitStyleDialogScreenshot() {
    MediatheqTheme { NavigationChoiceDialogContent(NavigationDialog.PortraitStyle, NavigationPreferences()) }
}

/** Media lists with every default (#123). */
@PreviewTest
@MqPreviews
@Composable
fun SettingsMediaListsScreenshot() = Page(SettingsPage.MediaLists)

/** Media lists with choices made: large icons, two buttons hidden, codec and bitrate, Rate on swipe right. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsMediaListsChosenScreenshot() {
    val lists =
        MediaListPreferences(
            showItemCounts = false,
            topButtonStyle = TopButtonStyle.LargeIcons,
            hiddenTopButtons = setOf(TopButton.Shuffle, TopButton.AddToQueue),
            trackInfo = TrackInfo.CodecAndBitrate,
            secondTrackInfo = TrackInfo.Rating,
            offlineOverlay = true,
            swipeRight = SwipeAction.Rate,
            swipeLeft = SwipeAction.Disabled,
        )
    MediatheqTheme {
        SettingsPageScreen(
            SettingsPage.MediaLists,
            content.copy(library = content.library.copy(mediaLists = lists)),
            {},
            {},
            {},
        )
    }
}

/** The top button row style dialog, Small icons checked. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsMediaListsTopButtonStyleDialogScreenshot() {
    MediatheqTheme { MediaListsDialogContent(MediaListsDialog.TopButtonStyle, MediaListPreferences()) }
}

/** The hidden top buttons dialog: checkboxes, Cancel and OK. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsMediaListsHiddenButtonsDialogScreenshot() {
    MediatheqTheme {
        MediaListsDialogContent(
            MediaListsDialog.HiddenTopButtons,
            MediaListPreferences(hiddenTopButtons = setOf(TopButton.PlayNext)),
        )
    }
}

/** The additional track information dialog, Duration checked. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsMediaListsTrackInfoDialogScreenshot() {
    MediatheqTheme { MediaListsDialogContent(MediaListsDialog.TrackInfo, MediaListPreferences()) }
}

/** The swipe action dialog, Add to queue checked. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsMediaListsSwipeDialogScreenshot() {
    MediatheqTheme { MediaListsDialogContent(MediaListsDialog.SwipeRight, MediaListPreferences()) }
}

/** Settings -> Now playing screens, defaults (#126). */
@PreviewTest
@MqPreviews
@Composable
fun SettingsNowPlayingScreenshot() = Page(SettingsPage.NowPlaying)

/** Docked, line progress, a plain Wavy bar (fallback unused), ratings off. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsNowPlayingChosenScreenshot() {
    val nowPlaying =
        NowPlayingPreferences(
            floatingMiniPlayer = false,
            miniProgress = MiniProgress.Line,
            background = PlayerBackground.Solid,
            progress = PlayerProgress.Wavy,
            coverTap = CoverTap.ShowLyrics,
            playButtonWhenPaused = true,
        )
    MediatheqTheme {
        SettingsPageScreen(
            SettingsPage.NowPlaying,
            content.copy(library = content.library.copy(nowPlaying = nowPlaying, ratingMode = RatingMode.Off)),
            {},
            {},
            {},
        )
    }
}

/** Every Now playing screens dialog, defaults checked. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsNowPlayingDialogsScreenshot() {
    MediatheqTheme {
        Column(verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
            NowPlayingDialogContent(NowPlayingDialog.MiniProgress, NowPlayingPreferences())
            NowPlayingDialogContent(NowPlayingDialog.Background, NowPlayingPreferences())
            NowPlayingDialogContent(NowPlayingDialog.CoverTap, NowPlayingPreferences())
        }
    }
}

/** The progress bar style dialog (six styles), Waveform (Sharp) checked; and the fallback dialog. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsNowPlayingProgressDialogsScreenshot() {
    MediatheqTheme {
        Column(verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
            NowPlayingDialogContent(NowPlayingDialog.Progress, NowPlayingPreferences())
            NowPlayingDialogContent(NowPlayingDialog.Fallback, NowPlayingPreferences())
        }
    }
}

/** Settings -> Page settings -> Home page (#127): every section with its handle and eye; hidden rows dimmed. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsHomePageScreenshot() = Page(SettingsPage.Home)

/** The Interface page with its new Page settings group. */
@PreviewTest
@Preview(name = "Light", device = "spec:width=412dp,height=1600dp,dpi=420")
@Composable
fun SettingsInterfacePageSettingsScreenshot() = Page(SettingsPage.Interface)

/** Settings -> Interface -> Page settings -> Library page (#129), default values. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsLibraryPageScreenshot() = Page(SettingsPage.Library)

/** The Library page's Organize dialog: Tracks unchecked, Years moved first. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsLibraryShortcutsDialogScreenshot() =
    MediatheqTheme {
        OrganizeDialogContent(
            "Library shortcuts",
            OrganizeDraft.of(
                listOf(
                    LibraryShortcut.Years,
                    LibraryShortcut.Albums,
                    LibraryShortcut.AlbumArtists,
                    LibraryShortcut.Playlists,
                ),
                LibraryShortcut.entries,
            ),
            label = { stringResource(it.label) },
        )
    }

@PreviewTest
@MqPreviews
@Composable
fun SettingsLibraryStyleDialogScreenshot() =
    MediatheqTheme { LibraryPageDialogContent(LibraryPageDialog.Style, LibraryPagePreferences()) }

/** Settings -> Album page (#132), all defaults. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsAlbumPageScreenshot() = Page(SettingsPage.Album)

@PreviewTest
@MqPreviews
@Composable
fun SettingsAlbumBackgroundDialogScreenshot() =
    MediatheqTheme { AlbumPageDialogContent(AlbumPageDialog.Background, AlbumPagePreferences()) }

/** Resume and Favorite checked: they move to the header's More menu. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsAlbumHiddenButtonsDialogScreenshot() =
    MediatheqTheme {
        AlbumPageDialogContent(
            AlbumPageDialog.HiddenButtons,
            AlbumPagePreferences(hiddenButtons = setOf(AlbumButton.Resume, AlbumButton.Favorite)),
        )
    }

@PreviewTest
@MqPreviews
@Composable
fun SettingsAlbumMoreSortDialogScreenshot() =
    MediatheqTheme { AlbumPageDialogContent(AlbumPageDialog.MoreSort, AlbumPagePreferences()) }

/** "Page content order": About moved first; the checks are fixed (switches hide sections). */
@PreviewTest
@MqPreviews
@Composable
fun SettingsAlbumSectionsDialogScreenshot() =
    MediatheqTheme {
        OrganizeDialogContent(
            "Page content order",
            albumSectionsDraft(listOf(AlbumSection.About, AlbumSection.Tracks, AlbumSection.MoreFromArtist)),
            label = { stringResource(it.label) },
        )
    }

/** Settings -> Artist page (#133), all defaults. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsArtistPageScreenshot() = Page(SettingsPage.Artist)

@PreviewTest
@MqPreviews
@Composable
fun SettingsArtistBackgroundDialogScreenshot() =
    MediatheqTheme { ArtistPageDialogContent(ArtistPageDialog.Background, ArtistPagePreferences()) }

@PreviewTest
@MqPreviews
@Composable
fun SettingsArtistPlayActionDialogScreenshot() =
    MediatheqTheme { ArtistPageDialogContent(ArtistPageDialog.PlayAction, ArtistPagePreferences()) }

/** Shuffle checked: it moves to the header's More menu. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsArtistHiddenButtonsDialogScreenshot() =
    MediatheqTheme {
        ArtistPageDialogContent(
            ArtistPageDialog.HiddenButtons,
            ArtistPagePreferences(hiddenButtons = setOf(ArtistButton.Shuffle)),
        )
    }

@PreviewTest
@MqPreviews
@Composable
fun SettingsArtistTopTracksCountDialogScreenshot() =
    MediatheqTheme { ArtistPageDialogContent(ArtistPageDialog.TopTracksCount, ArtistPagePreferences()) }

@PreviewTest
@MqPreviews
@Composable
fun SettingsArtistDynamicColorsDialogScreenshot() =
    MediatheqTheme { ArtistPageDialogContent(ArtistPageDialog.DynamicColors, ArtistPagePreferences()) }

/** "Page content order" with the default order; the checks are fixed (switches hide sections). */
@PreviewTest
@MqPreviews
@Composable
fun SettingsArtistSectionsDialogScreenshot() =
    MediatheqTheme {
        OrganizeDialogContent(
            "Page content order",
            artistSectionsDraft(ArtistPagePreferences.DEFAULT_SECTIONS),
            label = { stringResource(it.label) },
        )
    }

@PreviewTest
@MqPreviews
@Composable
fun SettingsArtistAlbumsSortDialogScreenshot() =
    MediatheqTheme { ArtistPageDialogContent(ArtistPageDialog.AlbumsSort, ArtistPagePreferences()) }

/** Settings -> Genre page (#134), all defaults. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsGenrePageScreenshot() = Page(SettingsPage.Genre)

@PreviewTest
@MqPreviews
@Composable
fun SettingsGenreBackgroundDialogScreenshot() =
    MediatheqTheme { GenrePageDialogContent(GenrePageDialog.Background, GenrePagePreferences()) }

/** Add to queue checked: it moves to the header's More menu. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsGenreHiddenButtonsDialogScreenshot() =
    MediatheqTheme {
        GenrePageDialogContent(
            GenrePageDialog.HiddenButtons,
            GenrePagePreferences(hiddenButtons = setOf(GenreButton.AddToQueue)),
        )
    }

@PreviewTest
@MqPreviews
@Composable
fun SettingsGenreDynamicColorsDialogScreenshot() =
    MediatheqTheme { GenrePageDialogContent(GenrePageDialog.DynamicColors, GenrePagePreferences()) }

@PreviewTest
@MqPreviews
@Composable
fun SettingsGenreTopTracksCountDialogScreenshot() =
    MediatheqTheme { GenrePageDialogContent(GenrePageDialog.TopTracksCount, GenrePagePreferences()) }

/** "Page content order" with the default order; the checks are fixed (switches hide sections). */
@PreviewTest
@MqPreviews
@Composable
fun SettingsGenreSectionsDialogScreenshot() =
    MediatheqTheme {
        OrganizeDialogContent(
            "Page content order",
            genreSectionsDraft(GenreSection.entries),
            label = { stringResource(it.label) },
        )
    }

/** Settings -> Playlist page (#135), all defaults. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsPlaylistPageScreenshot() = Page(SettingsPage.Playlist)

@PreviewTest
@MqPreviews
@Composable
fun SettingsPlaylistBackgroundDialogScreenshot() =
    MediatheqTheme { PlaylistPageDialogContent(PlaylistPageDialog.Background, PlaylistPagePreferences()) }

/** Resume and Favorite checked: they move to the header's More menu. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsPlaylistHiddenButtonsDialogScreenshot() =
    MediatheqTheme {
        PlaylistPageDialogContent(
            PlaylistPageDialog.HiddenButtons,
            PlaylistPagePreferences(hiddenButtons = setOf(PlaylistButton.Resume, PlaylistButton.Favorite)),
        )
    }

@PreviewTest
@MqPreviews
@Composable
fun SettingsPlaylistDynamicColorsDialogScreenshot() =
    MediatheqTheme { PlaylistPageDialogContent(PlaylistPageDialog.DynamicColors, PlaylistPagePreferences()) }

/** Settings -> Search page (#136), all defaults. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsSearchPageScreenshot() = Page(SettingsPage.Search)

@PreviewTest
@MqPreviews
@Composable
fun SettingsSearchTrackActionDialogScreenshot() =
    MediatheqTheme { SearchPageDialogContent(SearchPageDialog.TrackAction, SearchPagePreferences()) }

@PreviewTest
@MqPreviews
@Composable
fun SettingsSearchSortDialogScreenshot() =
    MediatheqTheme { SearchPageDialogContent(SearchPageDialog.Sort, SearchPagePreferences()) }

/** Settings -> Advanced settings (#137), all defaults. */
@PreviewTest
@MqPreviews
@Composable
fun SettingsAdvancedScreenshot() = Page(SettingsPage.Advanced)

@PreviewTest
@MqPreviews
@Composable
fun SettingsAdvancedKeepScreenOnDialogScreenshot() =
    MediatheqTheme { AdvancedDialogContent(AdvancedDialog.KeepScreenOn, AdvancedPreferences()) }

@PreviewTest
@MqPreviews
@Composable
fun SettingsAdvancedOrientationDialogScreenshot() =
    MediatheqTheme { AdvancedDialogContent(AdvancedDialog.Orientation, AdvancedPreferences()) }

@PreviewTest
@MqPreviews
@Composable
fun SettingsAdvancedCornersDialogScreenshot() =
    MediatheqTheme { AdvancedDialogContent(AdvancedDialog.Corners, AdvancedPreferences()) }
