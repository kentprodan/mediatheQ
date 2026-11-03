package io.github.kentprodan.mediatheq.core.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultLibraryPreferencesRepositoryTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private val repository = DefaultLibraryPreferencesRepository(LibraryPreferencesStore(dataStore))

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
    fun `defaults are chips, two columns and each tab's first sort`() =
        runTest {
            val preferences = repository.preferences.first()

            assertThat(preferences).isEqualTo(LibraryPreferences())
            assertThat(preferences.sortFor(LibraryFilter.Songs)).isEqualTo(LibrarySort.Name)
        }

    @Test
    fun `choices are kept`() =
        runTest {
            repository.setFilterStyle(FilterControlStyle.ConnectedButtons)
            repository.setAlbumColumns(4)
            repository.setSort(LibraryFilter.Albums, LibrarySort.Year)
            repository.setSort(LibraryFilter.Songs, LibrarySort.Album)

            val preferences = repository.preferences.first()
            assertThat(preferences.filterStyle).isEqualTo(FilterControlStyle.ConnectedButtons)
            assertThat(preferences.albumColumns).isEqualTo(4)
            assertThat(preferences.sortFor(LibraryFilter.Albums)).isEqualTo(LibrarySort.Year)
            assertThat(preferences.sortFor(LibraryFilter.Songs)).isEqualTo(LibrarySort.Album)
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
            }

            assertThat(repository.preferences.first()).isEqualTo(LibraryPreferences())
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
        }

    @Test
    fun `display choices are kept, and an unknown rating mode means whole stars`() =
        runTest {
            repository.setRatingMode(RatingMode.HalfStars)
            repository.setOfflineMode(true)
            assertThat(repository.preferences.first().offlineMode).isTrue()
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
                it[stringPreferencesKey("nav_entries")] = "Genres,Search,Home,Years"
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
}
