package io.github.kentprodan.mediatheq.core.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
import io.github.kentprodan.mediatheq.core.model.HomeDisplay
import io.github.kentprodan.mediatheq.core.model.HomeKind
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.HomeSectionSettings
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultLibraryPreferencesRepositoryTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private val repository = DefaultLibraryPreferencesRepository(LibraryPreferencesStore(dataStore))

    @Test
    fun `offline mode keeps its stored value, automatic offline defaults to no network, restore resets both only`() =
        runTest {
            // A value stored before #144 under the same key.
            dataStore.edit { it[booleanPreferencesKey("offline_mode")] = true }
            assertThat(repository.preferences.first().offlineMode).isTrue()
            assertThat(repository.preferences.first().autoOffline).isEqualTo(AutoOffline.NoNetwork)

            repository.setAutoOffline(AutoOffline.NotOnWifi)
            repository.setImages(ImagePreferences(wifiOnly = true))
            assertThat(repository.preferences.first().autoOffline).isEqualTo(AutoOffline.NotOnWifi)

            repository.restoreOfflineDefaults()
            val restored = repository.preferences.first()
            assertThat(restored.offlineMode).isFalse()
            assertThat(restored.autoOffline).isEqualTo(AutoOffline.NoNetwork)
            assertThat(restored.images).isEqualTo(ImagePreferences(wifiOnly = true))
        }

    @Test
    fun `image settings are off by default, kept, and null restores them only`() =
        runTest {
            assertThat(repository.preferences.first().images).isEqualTo(ImagePreferences(false, false, false))
            val images = ImagePreferences(persistentCache = true, wifiOnly = true, highQuality = true)
            repository.setAutoOffline(AutoOffline.Never)

            repository.setImages(images)
            assertThat(repository.preferences.first().images).isEqualTo(images)

            repository.setImages(null)
            assertThat(repository.preferences.first().images).isEqualTo(ImagePreferences())
            assertThat(repository.preferences.first().autoOffline).isEqualTo(AutoOffline.Never)
        }

    @Test
    fun `recent searches come newest first, without repeats, at most ten`() =
        runTest {
            (1..12).forEach { repository.addRecentSearch("q$it") }
            repository.addRecentSearch("Q5")

            assertThat(repository.recentSearches.first())
                .containsExactly("Q5", "q12", "q11", "q10", "q9", "q8", "q7", "q6", "q4", "q3")
                .inOrder()
        }

    @Test
    fun `a recent search is one line and blank ones are ignored`() =
        runTest {
            repository.addRecentSearch("  pasted\nwith   lines  ")
            repository.addRecentSearch(" \n ")

            assertThat(repository.recentSearches.first()).containsExactly("pasted with lines")
        }

    @Test
    fun `recent searches can be cleared`() =
        runTest {
            repository.addRecentSearch("mosoi")

            repository.clearRecentSearches()

            assertThat(repository.recentSearches.first()).isEmpty()
        }

    @Test
    fun `defaults are two columns and each tab's first sort`() =
        runTest {
            val preferences = repository.preferences.first()

            assertThat(preferences).isEqualTo(LibraryPreferences())
            assertThat(preferences.sortFor(LibraryFilter.Songs)).isEqualTo(LibrarySort.Name)
        }

    @Test
    fun `choices are kept`() =
        runTest {
            repository.setAlbumColumns(4)
            repository.setSort(LibraryFilter.Albums, LibrarySort.Year)
            repository.setSort(LibraryFilter.Songs, LibrarySort.Album)

            val preferences = repository.preferences.first()
            assertThat(preferences.albumColumns).isEqualTo(4)
            assertThat(preferences.sortFor(LibraryFilter.Albums)).isEqualTo(LibrarySort.Year)
            assertThat(preferences.sortFor(LibraryFilter.Songs)).isEqualTo(LibrarySort.Album)
        }

    @Test
    fun `a sort can be reversed per tab, kept, and a new sort starts in its natural direction`() =
        runTest {
            repository.setSort(LibraryFilter.Albums, LibrarySort.Year, reversed = true)
            repository.setSort(LibraryFilter.Songs, LibrarySort.Album)

            var preferences = repository.preferences.first()
            assertThat(preferences.sortFor(LibraryFilter.Albums)).isEqualTo(LibrarySort.Year)
            assertThat(preferences.isReversed(LibraryFilter.Albums)).isTrue()
            assertThat(preferences.isReversed(LibraryFilter.Songs)).isFalse()
            assertThat(dataStore.data.first()[stringPreferencesKey("sort_Albums")]).isEqualTo("Year,reversed")

            repository.setSort(LibraryFilter.Albums, LibrarySort.Name)
            preferences = repository.preferences.first()
            assertThat(preferences.sortFor(LibraryFilter.Albums)).isEqualTo(LibrarySort.Name)
            assertThat(preferences.isReversed(LibraryFilter.Albums)).isFalse()
        }

    @Test
    fun `reversing turns the stored sort around each time, also the default one`() =
        runTest {
            repository.setSort(LibraryFilter.Albums, LibrarySort.Year)
            repository.reverseSort(LibraryFilter.Albums)
            repository.reverseSort(LibraryFilter.Songs)

            var preferences = repository.preferences.first()
            assertThat(preferences.sortFor(LibraryFilter.Albums)).isEqualTo(LibrarySort.Year)
            assertThat(preferences.isReversed(LibraryFilter.Albums)).isTrue()
            assertThat(preferences.sortFor(LibraryFilter.Songs)).isEqualTo(LibrarySort.Name)
            assertThat(preferences.isReversed(LibraryFilter.Songs)).isTrue()

            repository.reverseSort(LibraryFilter.Albums)
            preferences = repository.preferences.first()
            assertThat(preferences.sortFor(LibraryFilter.Albums)).isEqualTo(LibrarySort.Year)
            assertThat(preferences.isReversed(LibraryFilter.Albums)).isFalse()
        }

    @Test
    fun `values a tab does not offer are refused`() =
        runTest {
            repository.setAlbumColumns(7)
            repository.setSort(LibraryFilter.Artists, LibrarySort.Year)

            assertThat(repository.preferences.first()).isEqualTo(LibraryPreferences())
        }

    @Test
    fun `old or broken stored values fall back to defaults`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("filter_style")] = "Carousel"
                it[intPreferencesKey("album_columns")] = 9
                it[stringPreferencesKey("sort_Songs")] = "Year"
                it[stringPreferencesKey("sort_Gone")] = "Name"
                it[stringPreferencesKey("sort_Albums")] = "Name,garbage"
                it[stringPreferencesKey("sort_Playlists")] = "Year,reversed"
            }

            assertThat(repository.preferences.first()).isEqualTo(LibraryPreferences())
        }

    @Test
    fun `the old tabbed Library's settings carry over, album columns kept and filter style dropped`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("filter_style")] = "ConnectedButtons"
                it[intPreferencesKey("album_columns")] = 4
            }

            assertThat(repository.preferences.first().albumColumns).isEqualTo(4)

            repository.setLibraryPage(LibraryPagePreferences(columns = 2))
            assertThat(dataStore.data.first()[stringPreferencesKey("filter_style")]).isNull()
            assertThat(repository.preferences.first().albumColumns).isEqualTo(4)
        }

    @Test
    fun `library page defaults are every tile, a grid of 3 columns, 5 in landscape, normal spacing`() =
        runTest {
            val page = repository.preferences.first().libraryPage

            assertThat(page.shortcuts).isEqualTo(LibraryShortcut.entries)
            assertThat(page.style).isEqualTo(LibraryHubStyle.Grid)
            assertThat(page.columns).isEqualTo(3)
            assertThat(page.landscapeColumns).isEqualTo(5)
            assertThat(page.spacing).isEqualTo(LibraryHubSpacing.Normal)
        }

    @Test
    fun `tiles saved before the video tiles get Movies and Series appended, once`() =
        runTest {
            // A list saved by an older version: no record of the tiles the app had then.
            dataStore.edit { it[stringPreferencesKey("lp_shortcuts")] = "Years,Albums" }

            assertThat(
                repository.preferences
                    .first()
                    .libraryPage.shortcuts,
            ).containsExactly(
                LibraryShortcut.Years,
                LibraryShortcut.Albums,
                LibraryShortcut.Movies,
                LibraryShortcut.Series,
            ).inOrder()

            // Removing Movies now is kept: the saved list knows every tile.
            repository.setLibraryPage(LibraryPagePreferences(listOf(LibraryShortcut.Years, LibraryShortcut.Series)))
            assertThat(
                repository.preferences
                    .first()
                    .libraryPage.shortcuts,
            ).containsExactly(LibraryShortcut.Years, LibraryShortcut.Series)
                .inOrder()
        }

    @Test
    fun `every library page choice is kept`() =
        runTest {
            val page =
                LibraryPagePreferences(
                    shortcuts = listOf(LibraryShortcut.Years, LibraryShortcut.Albums),
                    style = LibraryHubStyle.List,
                    columns = 4,
                    landscapeColumns = 6,
                    spacing = LibraryHubSpacing.Compact,
                )

            repository.setLibraryPage(page)

            assertThat(repository.preferences.first().libraryPage).isEqualTo(page)
        }

    @Test
    fun `library page values out of range, repeated or none are stored as defaults`() =
        runTest {
            repository.setLibraryPage(
                LibraryPagePreferences(shortcuts = emptyList(), columns = 9, landscapeColumns = 1),
            )
            assertThat(repository.preferences.first().libraryPage).isEqualTo(LibraryPagePreferences())

            repository.setLibraryPage(
                LibraryPagePreferences(shortcuts = listOf(LibraryShortcut.Tracks, LibraryShortcut.Tracks)),
            )
            assertThat(
                repository.preferences
                    .first()
                    .libraryPage.shortcuts,
            ).containsExactly(LibraryShortcut.Tracks)
        }

    @Test
    fun `unknown stored library page values fall back`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("lp_shortcuts")] = "Moods,Gone"
                it[stringPreferencesKey("lp_style")] = "Carousel"
                it[intPreferencesKey("lp_columns")] = 7
                it[stringPreferencesKey("lp_spacing")] = "Huge"
            }

            assertThat(repository.preferences.first().libraryPage).isEqualTo(LibraryPagePreferences())
        }

    @Test
    fun `restoring the library page defaults resets only that page`() =
        runTest {
            repository.setAlbumColumns(4)
            repository.setMediaLists(MediaListPreferences(showItemCounts = false))
            repository.setLibraryPage(LibraryPagePreferences(style = LibraryHubStyle.List, columns = 2))

            repository.restoreLibraryPageDefaults()

            val preferences = repository.preferences.first()
            assertThat(preferences.libraryPage).isEqualTo(LibraryPagePreferences())
            assertThat(preferences.albumColumns).isEqualTo(4)
            assertThat(preferences.mediaLists.showItemCounts).isFalse()
        }

    @Test
    fun `an unreadable file gives the defaults`() =
        runTest {
            dataStore.failing = true

            assertThat(repository.preferences.first()).isEqualTo(LibraryPreferences())
        }

    @Test
    fun `display defaults are Iris colors and whole stars`() =
        runTest {
            val preferences = repository.preferences.first()

            assertThat(preferences.offlineMode).isFalse()
            assertThat(preferences.ratingMode).isEqualTo(RatingMode.WholeStars)
            assertThat(preferences.qualityDetail).isFalse()
        }

    @Test
    fun `display choices are kept, and an unknown rating mode means whole stars`() =
        runTest {
            repository.setRatingMode(RatingMode.HalfStars)
            repository.setOfflineMode(true)
            repository.setQualityDetail(true)
            assertThat(repository.preferences.first().offlineMode).isTrue()
            assertThat(repository.preferences.first().qualityDetail).isTrue()
            assertThat(repository.preferences.first().ratingMode).isEqualTo(RatingMode.HalfStars)

            dataStore.edit { it[stringPreferencesKey("rating_mode")] = "Hearts" }
            assertThat(repository.preferences.first().ratingMode).isEqualTo(RatingMode.WholeStars)
        }

    @Test
    fun `the theme defaults to system mode, Iris, cover colors and the system font`() =
        runTest {
            assertThat(repository.preferences.first().theme)
                .isEqualTo(
                    ThemePreferences(
                        mode = ThemeMode.System,
                        colorSource = ColorSource.Iris,
                        customSeed = 0xFF5B45D6.toInt(),
                        nowPlayingColors = NowPlayingColors.Cover,
                        ignoreSurfaces = false,
                        instantColors = false,
                        font = AppFont.System,
                    ),
                )
        }

    @Test
    fun `every theme choice is kept, and a seed is made opaque`() =
        runTest {
            val theme =
                ThemePreferences(
                    ThemeMode.Black,
                    ColorSource.Custom,
                    0x00336699,
                    NowPlayingColors.CoverVibrant,
                    ignoreSurfaces = true,
                    instantColors = true,
                    font = AppFont.BebasNeue,
                )

            repository.setTheme(theme)

            assertThat(repository.preferences.first().theme).isEqualTo(theme.copy(customSeed = 0xFF336699.toInt()))
        }

    @Test
    fun `unknown theme values fall back to their defaults`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("theme_mode")] = "Sepia"
                it[stringPreferencesKey("font")] = "Comic Sans"
            }

            assertThat(repository.preferences.first().theme).isEqualTo(ThemePreferences())
        }

    @Test
    fun `the old Material You switch carries over until a color source is picked`() =
        runTest {
            dataStore.edit { it[booleanPreferencesKey("dynamic_color")] = true }
            assertThat(
                repository.preferences
                    .first()
                    .theme.colorSource,
            ).isEqualTo(ColorSource.MaterialYou)

            repository.setTheme(ThemePreferences(colorSource = ColorSource.Iris))
            assertThat(
                repository.preferences
                    .first()
                    .theme.colorSource,
            ).isEqualTo(ColorSource.Iris)
        }

    @Test
    fun `restoring the theme defaults resets only the theme`() =
        runTest {
            dataStore.edit { it[booleanPreferencesKey("dynamic_color")] = true }
            repository.setTheme(ThemePreferences(ThemeMode.Dark, font = AppFont.Inter))
            repository.setAlbumColumns(3)
            repository.setRatingMode(RatingMode.Off)
            repository.setOfflineMode(true)
            repository.setSort(LibraryFilter.Albums, LibrarySort.Year)
            repository.addRecentSearch("mosoi")

            repository.restoreThemeDefaults()

            val preferences = repository.preferences.first()
            assertThat(preferences.theme).isEqualTo(ThemePreferences())
            assertThat(preferences)
                .isEqualTo(
                    LibraryPreferences(
                        albumColumns = 3,
                        sorts = mapOf(LibraryFilter.Albums to LibrarySort.Year),
                        ratingMode = RatingMode.Off,
                        offlineMode = true,
                    ),
                )
            assertThat(repository.recentSearches.first()).containsExactly("mosoi")
        }

    @Test
    fun `navigation defaults are today's bar, bottom bar, rail and Play`() =
        runTest {
            val navigation = repository.preferences.first().navigation

            assertThat(navigation.entries)
                .containsExactly(MainNavEntry.Home, MainNavEntry.Library, MainNavEntry.Search, MainNavEntry.Downloads)
                .inOrder()
            assertThat(navigation.portraitStyle).isEqualTo(PortraitNavStyle.BottomBar)
            assertThat(navigation.landscapeStyle).isEqualTo(LandscapeNavStyle.NavigationRail)
            assertThat(navigation.transparencyPercent).isEqualTo(0)
            assertThat(navigation.slowStartMessages).isTrue()
            assertThat(navigation.trackAction).isEqualTo(TrackAction.Play)
            assertThat(navigation.expandPlayer).isFalse()
        }

    @Test
    fun `every navigation choice is kept`() =
        runTest {
            val chosen =
                NavigationPreferences(
                    entries = listOf(MainNavEntry.Tracks, MainNavEntry.Home, MainNavEntry.Search),
                    portraitStyle = PortraitNavStyle.FloatingToolbarWithFab,
                    landscapeStyle = LandscapeNavStyle.SameAsPortrait,
                    transparencyPercent = 50,
                    slowStartMessages = false,
                    trackAction = TrackAction.PlayNext,
                    expandPlayer = true,
                )

            repository.setNavigation(chosen)

            assertThat(repository.preferences.first().navigation).isEqualTo(chosen)
        }

    @Test
    fun `entries keep Home, drop repeats and keep at most six`() =
        runTest {
            repository.setNavigation(
                NavigationPreferences(
                    entries =
                        listOf(
                            MainNavEntry.Albums,
                            MainNavEntry.Albums,
                            MainNavEntry.Library,
                            MainNavEntry.AlbumArtists,
                            MainNavEntry.Playlists,
                            MainNavEntry.Tracks,
                            MainNavEntry.Search,
                            MainNavEntry.Downloads,
                        ),
                ),
            )

            assertThat(
                repository.preferences
                    .first()
                    .navigation.entries,
            ).containsExactly(
                MainNavEntry.Home,
                MainNavEntry.Albums,
                MainNavEntry.Library,
                MainNavEntry.AlbumArtists,
                MainNavEntry.Playlists,
                MainNavEntry.Tracks,
            ).inOrder()
        }

    @Test
    fun `transparency is clamped to 0-100 in steps of ten`() =
        runTest {
            repository.setNavigation(NavigationPreferences(transparencyPercent = 46))
            assertThat(
                repository.preferences
                    .first()
                    .navigation.transparencyPercent,
            ).isEqualTo(50)

            repository.setNavigation(NavigationPreferences(transparencyPercent = 140))
            assertThat(
                repository.preferences
                    .first()
                    .navigation.transparencyPercent,
            ).isEqualTo(100)

            repository.setNavigation(NavigationPreferences(transparencyPercent = -5))
            assertThat(
                repository.preferences
                    .first()
                    .navigation.transparencyPercent,
            ).isEqualTo(0)
        }

    @Test
    fun `unknown stored navigation values are dropped or fall back to defaults`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("nav_entries")] = "Moods,Search,Home,Years"
                it[stringPreferencesKey("nav_portrait_style")] = "Drawer"
                it[stringPreferencesKey("track_action")] = "Shuffle"
                it[intPreferencesKey("nav_transparency")] = 33
            }

            val navigation = repository.preferences.first().navigation

            assertThat(navigation.entries).containsExactly(MainNavEntry.Search, MainNavEntry.Home).inOrder()
            assertThat(navigation.portraitStyle).isEqualTo(PortraitNavStyle.BottomBar)
            assertThat(navigation.trackAction).isEqualTo(TrackAction.Play)
            assertThat(navigation.transparencyPercent).isEqualTo(30)
        }

    @Test
    fun `stored entries without Home get Home first`() =
        runTest {
            dataStore.edit { it[stringPreferencesKey("nav_entries")] = "Search,Tracks" }

            assertThat(
                repository.preferences
                    .first()
                    .navigation.entries,
            ).containsExactly(MainNavEntry.Home, MainNavEntry.Search, MainNavEntry.Tracks)
                .inOrder()
        }

    @Test
    fun `restoring the navigation defaults resets only that page`() =
        runTest {
            val theme = ThemePreferences(ThemeMode.Dark)
            repository.setTheme(theme)
            repository.setAlbumColumns(3)
            repository.setNavigation(
                NavigationPreferences(
                    entries = listOf(MainNavEntry.Home, MainNavEntry.Tracks),
                    trackAction = TrackAction.AddToQueue,
                    expandPlayer = true,
                ),
            )

            repository.restoreNavigationDefaults()

            assertThat(repository.preferences.first())
                .isEqualTo(LibraryPreferences(albumColumns = 3, theme = theme))
        }

    @Test
    fun `media lists defaults are the ones of the issue`() =
        runTest {
            val lists = repository.preferences.first().mediaLists

            assertThat(lists.showItemCounts).isTrue()
            assertThat(lists.topButtonStyle).isEqualTo(TopButtonStyle.SmallIcons)
            assertThat(lists.hiddenTopButtons).isEmpty()
            assertThat(lists.invertedSortArrows).isFalse()
            assertThat(lists.hideBackButton).isFalse()
            assertThat(lists.trackInfo).isEqualTo(TrackInfo.Duration)
            assertThat(lists.secondTrackInfo).isEqualTo(TrackInfo.None)
            assertThat(lists.centerGridText).isTrue()
            assertThat(lists.favoriteOverlay).isTrue()
            assertThat(lists.offlineOverlay).isFalse()
            assertThat(lists.swipeRight).isEqualTo(SwipeAction.AddToQueue)
            assertThat(lists.swipeLeft).isEqualTo(SwipeAction.PlayNext)
        }

    @Test
    fun `every media lists choice is kept`() =
        runTest {
            val chosen =
                MediaListPreferences(
                    showItemCounts = false,
                    topButtonStyle = TopButtonStyle.LargeIcons,
                    hiddenTopButtons = setOf(TopButton.Shuffle, TopButton.AddToQueue),
                    invertedSortArrows = true,
                    hideBackButton = true,
                    trackInfo = TrackInfo.CodecAndBitrate,
                    secondTrackInfo = TrackInfo.Rating,
                    centerGridText = false,
                    favoriteOverlay = false,
                    offlineOverlay = true,
                    swipeRight = SwipeAction.Rate,
                    swipeLeft = SwipeAction.Disabled,
                )

            repository.setMediaLists(chosen)

            assertThat(repository.preferences.first().mediaLists).isEqualTo(chosen)
        }

    @Test
    fun `no hidden top buttons after some were hidden reads back as none`() =
        runTest {
            repository.setMediaLists(MediaListPreferences(hiddenTopButtons = setOf(TopButton.Play)))
            repository.setMediaLists(MediaListPreferences(hiddenTopButtons = emptySet()))

            assertThat(
                repository.preferences
                    .first()
                    .mediaLists.hiddenTopButtons,
            ).isEmpty()
        }

    @Test
    fun `unknown stored media lists values are dropped or fall back to defaults`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("ml_hidden_top_buttons")] = "PersonalMix,Shuffle"
                it[stringPreferencesKey("ml_track_info")] = "Bpm"
                it[stringPreferencesKey("ml_swipe_left")] = "Delete"
                it[stringPreferencesKey("ml_top_button_style")] = "TextOnly"
            }

            val lists = repository.preferences.first().mediaLists

            assertThat(lists.hiddenTopButtons).containsExactly(TopButton.Shuffle)
            assertThat(lists.trackInfo).isEqualTo(TrackInfo.Duration)
            assertThat(lists.swipeLeft).isEqualTo(SwipeAction.PlayNext)
            assertThat(lists.topButtonStyle).isEqualTo(TopButtonStyle.SmallIcons)
        }

    @Test
    fun `restoring the media lists defaults resets only that page`() =
        runTest {
            val navigation = NavigationPreferences(trackAction = TrackAction.PlayNext)
            repository.setNavigation(navigation)
            repository.setRatingMode(RatingMode.HalfStars)
            repository.setMediaLists(
                MediaListPreferences(trackInfo = TrackInfo.Bitrate, swipeRight = SwipeAction.Download),
            )

            repository.restoreMediaListsDefaults()

            assertThat(repository.preferences.first())
                .isEqualTo(LibraryPreferences(ratingMode = RatingMode.HalfStars, navigation = navigation))
        }

    @Test
    fun `now playing screens defaults are the ones of the issue`() =
        runTest {
            assertThat(repository.preferences.first().nowPlaying)
                .isEqualTo(
                    NowPlayingPreferences(
                        floatingMiniPlayer = true,
                        miniSwipeToSkip = true,
                        miniProgress = MiniProgress.Background,
                        background = PlayerBackground.Gradient,
                        progress = PlayerProgress.WaveformSharp,
                        fallback = ProgressFallback.Basic,
                        showRating = true,
                        coverTap = CoverTap.PlayPause,
                        coverSwipe = true,
                        playButtonWhenPaused = false,
                    ),
                )
        }

    @Test
    fun `every now playing choice is kept, unknown stored values fall back`() =
        runTest {
            val chosen =
                NowPlayingPreferences(
                    floatingMiniPlayer = false,
                    miniSwipeToSkip = false,
                    miniProgress = MiniProgress.Line,
                    background = PlayerBackground.Theme,
                    progress = PlayerProgress.Wavy,
                    fallback = ProgressFallback.Wavy,
                    showRating = false,
                    coverTap = CoverTap.ShowLyrics,
                    coverSwipe = false,
                    playButtonWhenPaused = true,
                )
            repository.setNowPlaying(chosen)
            assertThat(repository.preferences.first().nowPlaying).isEqualTo(chosen)

            dataStore.edit {
                it[stringPreferencesKey("np_progress")] = "Spectrum"
                it[stringPreferencesKey("np_cover_tap")] = "Like"
            }
            val read = repository.preferences.first().nowPlaying
            assertThat(read.progress).isEqualTo(PlayerProgress.WaveformSharp)
            assertThat(read.coverTap).isEqualTo(CoverTap.PlayPause)
        }

    @Test
    fun `restoring the now playing defaults resets only that page`() =
        runTest {
            val lists = MediaListPreferences(trackInfo = TrackInfo.Bitrate)
            repository.setMediaLists(lists)
            repository.setQualityDetail(true)
            repository.setNowPlaying(NowPlayingPreferences(background = PlayerBackground.Solid, coverSwipe = false))

            repository.restoreNowPlayingDefaults()

            assertThat(repository.preferences.first())
                .isEqualTo(LibraryPreferences(mediaLists = lists, qualityDetail = true))
        }

    @Test
    fun `with nothing stored Home shows today's sections and more, in the table order`() =
        runTest {
            val home = repository.preferences.first().home

            assertThat(home.order).isEqualTo(HomeSection.entries)
            // Today's Home rows are all shown by default.
            assertThat(home.shown)
                .containsAtLeast(
                    HomeSection.LastPlayedAlbums,
                    HomeSection.RecentlyAddedAlbums,
                    HomeSection.FavoriteAlbums,
                    HomeSection.FavoriteTracks,
                )
            assertThat(home.hidden).containsExactly(
                HomeSection.RecentlyAddedTracks,
                HomeSection.RecentlyAddedArtists,
                HomeSection.LastPlayedTracks,
                HomeSection.LastPlayedArtists,
                HomeSection.MostPlayedAlbums,
                HomeSection.MostPlayedArtists,
                HomeSection.RandomTracks,
                HomeSection.RediscoverTracks,
                HomeSection.FiveStarTracks,
                HomeSection.RecentlyAddedMovies,
                HomeSection.RecentlyAddedEpisodes,
            )
        }

    @Test
    fun `a stored order keeps its sections first, unknown names go and missing sections follow`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("home_order")] = "FavoriteTracks,Radios,FavoriteAlbums,FavoriteTracks"
            }

            val order =
                repository.preferences
                    .first()
                    .home.order

            assertThat(order.take(2)).containsExactly(HomeSection.FavoriteTracks, HomeSection.FavoriteAlbums).inOrder()
            assertThat(order).hasSize(HomeSection.entries.size)
            assertThat(order.drop(2)).isEqualTo(
                HomeSection.entries - setOf(HomeSection.FavoriteTracks, HomeSection.FavoriteAlbums),
            )
        }

    @Test
    fun `order and visibility are kept, nothing hidden included, and reset rows brings both back`() =
        runTest {
            val order = HomeSection.entries.reversed()
            repository.setHomeOrder(order)
            repository.setHomeHidden(emptySet())
            assertThat(repository.preferences.first().home).isEqualTo(HomePreferences(order, emptySet()))

            repository.resetHomeRows()
            assertThat(repository.preferences.first().home).isEqualTo(HomePreferences())

            repository.setHomeHidden(setOf(HomeSection.FavoriteAlbums))
            repository.setMediaLists(MediaListPreferences(trackInfo = TrackInfo.Bitrate))
            repository.restoreHomeDefaults()
            val restored = repository.preferences.first()
            assertThat(restored.home).isEqualTo(HomePreferences())
            assertThat(restored.mediaLists.trackInfo).isEqualTo(TrackInfo.Bitrate)
        }

    @Test
    fun `the album page settings are kept, and restoring them resets only that page`() =
        runTest {
            val page =
                AlbumPagePreferences(
                    headerBackground = AlbumHeaderBackground.CoverColor,
                    showQuality = false,
                    showRatingBar = true,
                    hiddenButtons = setOf(AlbumButton.Resume, AlbumButton.Favorite),
                    sectionOrder = listOf(AlbumSection.About, AlbumSection.Tracks, AlbumSection.MoreFromArtist),
                    showMoreFromArtist = false,
                    moreAlbumsSort = MoreAlbumsSort.Title,
                    alwaysShowTrackArtists = true,
                    showTrackNumbers = false,
                    showTrackThumbnails = true,
                    multilineTrackTitles = true,
                    showAbout = false,
                )
            repository.setAlbumPage(page)
            repository.setQualityDetail(true)
            assertThat(repository.preferences.first().albumPage).isEqualTo(page)

            repository.restoreAlbumPageDefaults()

            assertThat(repository.preferences.first()).isEqualTo(LibraryPreferences(qualityDetail = true))
        }

    @Test
    fun `a stored section order drops unknown names and completes the missing sections, none hidden stays none`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("ap_sections")] = "About,Gone,About"
                it[stringPreferencesKey("ap_hidden_buttons")] = ""
                it[stringPreferencesKey("ap_background")] = "Blur"
            }

            val page = repository.preferences.first().albumPage

            assertThat(page.sectionOrder)
                .containsExactly(AlbumSection.About, AlbumSection.Tracks, AlbumSection.MoreFromArtist)
                .inOrder()
            assertThat(page.hiddenButtons).isEmpty()
            assertThat(page.headerBackground).isEqualTo(AlbumHeaderBackground.CoverImage)
        }

    @Test
    fun `the artist page settings are kept, and restoring them resets only that page`() =
        runTest {
            val page =
                ArtistPagePreferences(
                    headerBackground = ArtistHeaderBackground.ImageColor,
                    hiddenButtons = setOf(ArtistButton.Shuffle, ArtistButton.Favorite),
                    playAction = ArtistPlayAction.TopTracks,
                    sectionOrder =
                        listOf(
                            ArtistSection.About,
                            ArtistSection.Albums,
                            ArtistSection.TopTracks,
                            ArtistSection.AppearsOn,
                        ),
                    albumsSort = MoreAlbumsSort.Title,
                    albumColumns = 2,
                    landscapeAlbumColumns = 6,
                    showAppearsOn = false,
                    showTopTracks = false,
                    topTracksCount = 30,
                    showAbout = false,
                    dynamicColors = false,
                )
            val album = AlbumPagePreferences(showAbout = false)
            repository.setArtistPage(page)
            repository.setAlbumPage(album)
            repository.setAlbumColumns(4)
            assertThat(repository.preferences.first().artistPage).isEqualTo(page)

            repository.restoreArtistPageDefaults()

            val restored = repository.preferences.first()
            assertThat(restored.artistPage).isEqualTo(ArtistPagePreferences())
            assertThat(restored.albumPage).isEqualTo(album)
            assertThat(restored.albumColumns).isEqualTo(4)
        }

    @Test
    fun `a stored artist page drops unknown names, completes the order and clamps the counts`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("arp_sections")] = "About,Gone,About"
                it[stringPreferencesKey("arp_hidden_buttons")] = ""
                it[stringPreferencesKey("arp_background")] = "Blur"
                it[intPreferencesKey("arp_columns")] = 9
                it[intPreferencesKey("arp_top_tracks_count")] = 1
            }

            val page = repository.preferences.first().artistPage

            assertThat(page.sectionOrder)
                .containsExactly(
                    ArtistSection.About,
                    ArtistSection.Albums,
                    ArtistSection.TopTracks,
                    ArtistSection.AppearsOn,
                ).inOrder()
            assertThat(page.hiddenButtons).isEmpty()
            assertThat(page.headerBackground).isEqualTo(ArtistHeaderBackground.Backdrop)
            assertThat(page.albumColumns).isEqualTo(4)
            assertThat(page.topTracksCount).isEqualTo(5)
        }

    @Test
    fun `the genre page settings are kept, and restoring them resets only that page`() =
        runTest {
            val page =
                GenrePagePreferences(
                    headerBackground = GenreHeaderBackground.None,
                    hiddenButtons = setOf(GenreButton.AddToQueue),
                    sectionOrder =
                        listOf(
                            GenreSection.TopTracks,
                            GenreSection.Albums,
                            GenreSection.Artists,
                            GenreSection.AlbumArtists,
                        ),
                    showAlbums = false,
                    showArtists = false,
                    showAlbumArtists = true,
                    showTopTracks = false,
                    topTracksCount = 40,
                    dynamicColors = true,
                )
            val artist = ArtistPagePreferences(showAbout = false)
            repository.setGenrePage(page)
            repository.setArtistPage(artist)
            assertThat(repository.preferences.first().genrePage).isEqualTo(page)

            repository.restoreGenrePageDefaults()

            val restored = repository.preferences.first()
            assertThat(restored.genrePage).isEqualTo(GenrePagePreferences())
            assertThat(restored.artistPage).isEqualTo(artist)
        }

    @Test
    fun `a stored genre page drops unknown names, completes the order and clamps the count`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("gp_sections")] = "TopTracks,Gone"
                it[stringPreferencesKey("gp_hidden_buttons")] = ""
                it[stringPreferencesKey("gp_background")] = "Blur"
                it[intPreferencesKey("gp_top_tracks_count")] = 99
            }

            val page = repository.preferences.first().genrePage

            assertThat(page.sectionOrder)
                .containsExactly(
                    GenreSection.TopTracks,
                    GenreSection.Artists,
                    GenreSection.AlbumArtists,
                    GenreSection.Albums,
                ).inOrder()
            assertThat(page.hiddenButtons).isEmpty()
            assertThat(page.headerBackground).isEqualTo(GenreHeaderBackground.Image)
            assertThat(page.topTracksCount).isEqualTo(50)
            assertThat(page.dynamicColors).isFalse()
        }

    @Test
    fun `the playlist page settings are kept, and restoring them resets only that page`() =
        runTest {
            val page =
                PlaylistPagePreferences(
                    headerBackground = PlaylistHeaderBackground.ImageColor,
                    hiddenButtons = setOf(PlaylistButton.Resume, PlaylistButton.Favorite),
                    dynamicColors = true,
                )
            val genre = GenrePagePreferences(showAlbums = false)
            repository.setPlaylistPage(page)
            repository.setGenrePage(genre)
            assertThat(repository.preferences.first().playlistPage).isEqualTo(page)

            repository.restorePlaylistPageDefaults()

            val restored = repository.preferences.first()
            assertThat(restored.playlistPage).isEqualTo(PlaylistPagePreferences())
            assertThat(restored.genrePage).isEqualTo(genre)
        }

    @Test
    fun `a stored playlist page drops unknown names, and none hidden stays none`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("pp_hidden_buttons")] = "Shuffle,Gone"
                it[stringPreferencesKey("pp_background")] = "Blur"
            }
            assertThat(repository.preferences.first().playlistPage)
                .isEqualTo(PlaylistPagePreferences(hiddenButtons = setOf(PlaylistButton.Shuffle)))

            repository.setPlaylistPage(PlaylistPagePreferences(hiddenButtons = emptySet()))
            assertThat(
                repository.preferences
                    .first()
                    .playlistPage.hiddenButtons,
            ).isEmpty()
        }

    @Test
    fun `the search page settings are kept, and restoring them resets only that page`() =
        runTest {
            val page =
                SearchPagePreferences(
                    singleLineChips = false,
                    showFavorite = false,
                    trackAction = TrackAction.PlayNext,
                    sort = SearchSort.Relevance,
                )
            val playlist = PlaylistPagePreferences(dynamicColors = true)
            repository.setSearchPage(page)
            repository.setPlaylistPage(playlist)
            assertThat(repository.preferences.first().searchPage).isEqualTo(page)

            repository.restoreSearchPageDefaults()

            val restored = repository.preferences.first()
            assertThat(restored.searchPage).isEqualTo(SearchPagePreferences())
            assertThat(restored.playlistPage).isEqualTo(playlist)
        }

    @Test
    fun `a stored search page with unknown names keeps the defaults, Same as default included`() =
        runTest {
            repository.setSearchPage(SearchPagePreferences(trackAction = TrackAction.Play))
            repository.setSearchPage(SearchPagePreferences(trackAction = null))
            assertThat(
                repository.preferences
                    .first()
                    .searchPage.trackAction,
            ).isNull()

            dataStore.edit {
                it[stringPreferencesKey("sp_track_action")] = "Shout"
                it[stringPreferencesKey("sp_sort")] = "Year"
            }
            assertThat(repository.preferences.first().searchPage).isEqualTo(SearchPagePreferences())
        }

    @Test
    fun `the advanced settings are kept, and restoring them resets only that page`() =
        runTest {
            val advanced =
                AdvancedPreferences(
                    keepScreenOn = KeepScreenOn.NowPlaying,
                    orientation = ScreenOrientation.Landscape,
                    corners = CornerStyle.Large,
                    showTrackNumber = true,
                    hideStatusBar = true,
                )
            val search = SearchPagePreferences(showFavorite = false)
            repository.setAdvanced(advanced)
            repository.setSearchPage(search)
            assertThat(repository.preferences.first().advanced).isEqualTo(advanced)

            repository.restoreAdvancedDefaults()

            val restored = repository.preferences.first()
            assertThat(restored.advanced).isEqualTo(AdvancedPreferences())
            assertThat(restored.searchPage).isEqualTo(search)
        }

    @Test
    fun `stored advanced settings with unknown names keep the defaults`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("adv_keep_screen_on")] = "Sometimes"
                it[stringPreferencesKey("adv_orientation")] = "Upside"
                it[stringPreferencesKey("adv_corners")] = "Square"
            }
            assertThat(repository.preferences.first().advanced).isEqualTo(AdvancedPreferences())
        }

    @Test
    fun `a section's settings are kept, read back and clamped, the other sections keep their defaults`() =
        runTest {
            val changed =
                HomeSection.FavoriteAlbums.defaultSettings.copy(
                    display = HomeDisplay.Grid,
                    gridRows = 9,
                    maxItems = 2,
                    centerText = false,
                    playButton = true,
                    favoriteOverlay = true,
                )

            repository.setHomeSectionSettings(HomeSection.FavoriteAlbums, changed)

            val home = repository.preferences.first().home
            assertThat(home.settingsOf(HomeSection.FavoriteAlbums)).isEqualTo(changed.copy(gridRows = 3, maxItems = 5))
            assertThat(home.settingsOf(HomeSection.RandomAlbums)).isEqualTo(HomeSection.RandomAlbums.defaultSettings)
        }

    @Test
    fun `apply to all reaches every section of the kind only, restore one section resets it alone`() =
        runTest {
            val big = HomeSectionSettings(maxItems = 40, hideSubtitles = true)

            repository.applyHomeSectionSettings(HomeKind.Albums, big)
            repository.restoreHomeSection(HomeSection.RandomAlbums)

            val home = repository.preferences.first().home
            HomeSection.entries.filter { it.kind == HomeKind.Albums && it != HomeSection.RandomAlbums }.forEach {
                assertThat(home.settingsOf(it)).isEqualTo(big)
            }
            assertThat(home.settingsOf(HomeSection.RandomAlbums)).isEqualTo(HomeSection.RandomAlbums.defaultSettings)
            assertThat(home.settingsOf(HomeSection.RandomArtists)).isEqualTo(HomeSection.RandomArtists.defaultSettings)
        }

    @Test
    fun `reset active rows keeps the sections' settings, restore defaults drops them back to Media lists`() =
        runTest {
            val section = HomeSection.FavoriteArtists
            repository.setHomeSectionSettings(
                section,
                section.defaultSettings.copy(favoriteOverlay = false, maxItems = 30),
            )

            repository.resetHomeRows()
            assertThat(
                repository.preferences
                    .first()
                    .home
                    .settingsOf(section)
                    .maxItems,
            ).isEqualTo(30)

            repository.restoreHomeDefaults()
            val restored =
                repository.preferences
                    .first()
                    .home
                    .settingsOf(section)
            assertThat(restored).isEqualTo(section.defaultSettings)
            assertThat(restored.favoriteOverlay).isNull()
        }

    @Test
    fun `a broken or unknown stored value reads as the section's defaults, known pairs kept`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("home_section_FavoriteAlbums")] =
                    "max=abc;display=Spiral;future=1;header=false;rows"
                it[stringPreferencesKey("home_section_NoSuchSection")] = "max=20"
            }

            val home = repository.preferences.first().home
            assertThat(home.settingsOf(HomeSection.FavoriteAlbums))
                .isEqualTo(HomeSection.FavoriteAlbums.defaultSettings.copy(showHeader = false))
            assertThat(home.settings.keys).containsExactly(HomeSection.FavoriteAlbums)
        }

    @Test
    fun `a Home order stored before the video sections gets them after the mix buttons, the off ones hidden`() =
        runTest {
            repository.setHomeOrder(listOf(HomeSection.Shortcuts, HomeSection.FavoriteAlbums))
            repository.setHomeHidden(setOf(HomeSection.RandomTracks))
            // As stored before #275: neither list knows the video sections.
            val stored = HomeSection.entries.filter { it.kind != HomeKind.Videos }
            repository.setHomeOrder(stored)

            val home = repository.preferences.first().home

            assertThat(home.order.take(5))
                .containsExactly(
                    HomeSection.Shortcuts,
                    HomeSection.ContinueWatching,
                    HomeSection.NextUp,
                    HomeSection.RecentlyAddedMovies,
                    HomeSection.RecentlyAddedEpisodes,
                ).inOrder()
            assertThat(home.hidden).containsAtLeast(HomeSection.RecentlyAddedMovies, HomeSection.RecentlyAddedEpisodes)
            assertThat(home.hidden).containsNoneOf(HomeSection.ContinueWatching, HomeSection.NextUp)
        }
}
