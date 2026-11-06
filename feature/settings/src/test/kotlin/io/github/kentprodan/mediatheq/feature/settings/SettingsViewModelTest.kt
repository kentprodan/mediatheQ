package io.github.kentprodan.mediatheq.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AdvancedPreferences
import io.github.kentprodan.mediatheq.core.data.AlbumHeaderBackground
import io.github.kentprodan.mediatheq.core.data.AlbumPagePreferences
import io.github.kentprodan.mediatheq.core.data.AlbumSection
import io.github.kentprodan.mediatheq.core.data.AppFont
import io.github.kentprodan.mediatheq.core.data.ArtistHeaderBackground
import io.github.kentprodan.mediatheq.core.data.ArtistPagePreferences
import io.github.kentprodan.mediatheq.core.data.ArtistSection
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.ColorSource
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.data.GenrePagePreferences
import io.github.kentprodan.mediatheq.core.data.GenreSection
import io.github.kentprodan.mediatheq.core.data.HomePreferences
import io.github.kentprodan.mediatheq.core.data.KeepScreenOn
import io.github.kentprodan.mediatheq.core.data.LibraryHubStyle
import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.MainNavEntry
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.data.NowPlayingPreferences
import io.github.kentprodan.mediatheq.core.data.PlaybackBehavior
import io.github.kentprodan.mediatheq.core.data.PlayerProgress
import io.github.kentprodan.mediatheq.core.data.PlaylistButton
import io.github.kentprodan.mediatheq.core.data.PlaylistPagePreferences
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.SearchPagePreferences
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SwipeAction
import io.github.kentprodan.mediatheq.core.data.ThemeMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.TrackAction
import io.github.kentprodan.mediatheq.core.data.TrackInfo
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.testing.FakeAudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = FakeSessionRepository()
    private val audio = FakeAudioPreferencesRepository()
    private val library = FakeLibraryPreferencesRepository()
    private val playback = FakePlaybackConnection()
    private val viewModel by lazy { SettingsViewModel(session, audio, library, playback) }

    private fun signIn() {
        session.session.value = SessionState.LoggedIn("Home", "Ana")
    }

    @Test
    fun `signed out it shows nothing`() =
        runTest {
            viewModel.uiState.test { assertThat(awaitItem()).isEqualTo(SettingsUiState.Loading) }
        }

    @Test
    fun `the account, the preferences and the output show`() =
        runTest {
            signIn()
            playback.state.value = PlayerUiModel(output = AudioOutputInfo(usbDacConnected = true))

            viewModel.uiState.test {
                assertThat(expectMostRecentItem())
                    .isEqualTo(
                        SettingsUiState.Content(
                            "Home",
                            "Ana",
                            AudioPreferences(),
                            LibraryPreferences(),
                            AudioOutputInfo(usbDacConnected = true),
                        ),
                    )
            }
        }

    @Test
    fun `a changed preference shows at once`() =
        runTest {
            signIn()
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.onAction(SettingsAction.SetRatingMode(RatingMode.HalfStars))

                assertThat((awaitItem() as SettingsUiState.Content).library.ratingMode).isEqualTo(RatingMode.HalfStars)
            }
        }

    @Test
    fun `playback and storage actions reach the audio preferences`() =
        runTest {
            signIn()

            viewModel.onAction(SettingsAction.SetStreamingQuality(metered = true, StreamingQuality.Kbps128))
            viewModel.onAction(SettingsAction.SetStreamingQuality(metered = false, StreamingQuality.Kbps256))
            viewModel.onAction(SettingsAction.SetNormalization(Normalization.Off))
            viewModel.onAction(SettingsAction.SetStreamingCacheGb(8))
            viewModel.onAction(SettingsAction.SetDownloadsUnmeteredOnly(false))

            assertThat(audio.preferences.value)
                .isEqualTo(
                    AudioPreferences(
                        normalization = Normalization.Off,
                        unmeteredQuality = StreamingQuality.Kbps256,
                        meteredQuality = StreamingQuality.Kbps128,
                        streamingCacheGb = 8,
                        downloadsUnmeteredOnly = false,
                    ),
                )
        }

    @Test
    fun `audio actions reach the audio preferences`() =
        runTest {
            viewModel.onAction(SettingsAction.SetBitPerfectUsb(true))
            viewModel.onAction(SettingsAction.SetEqualizerOn(true))
            viewModel.onAction(SettingsAction.SetEqPreset(EqPreset.Vocal))
            viewModel.onAction(SettingsAction.SetEqBand(0, 5f))

            val preferences = audio.preferences.value
            assertThat(preferences.bitPerfectUsb).isTrue()
            assertThat(preferences.equalizerOn).isTrue()
            assertThat(preferences.eqPreset).isEqualTo(EqPreset.Custom)
            assertThat(preferences.eqGainsDb.first()).isEqualTo(5f)
        }

    @Test
    fun `display actions reach the library preferences`() =
        runTest {
            viewModel.onAction(SettingsAction.SetAlbumColumns(3))
            viewModel.onAction(SettingsAction.SetRatingMode(RatingMode.Off))
            viewModel.onAction(SettingsAction.SetOfflineMode(true))

            assertThat(library.preferences.value)
                .isEqualTo(
                    LibraryPreferences(
                        albumColumns = 3,
                        ratingMode = RatingMode.Off,
                        offlineMode = true,
                    ),
                )
        }

    @Test
    fun `log out signs out`() =
        runTest {
            signIn()

            viewModel.onAction(SettingsAction.LogOut)

            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut())
        }

    @Test
    fun `theme actions reach the library preferences, and restore resets only the theme`() =
        runTest {
            val theme = ThemePreferences(ThemeMode.Black, ColorSource.Custom, font = AppFont.Lato)
            viewModel.onAction(SettingsAction.SetAlbumColumns(3))

            viewModel.onAction(SettingsAction.SetTheme(theme))
            assertThat(library.preferences.value.theme).isEqualTo(theme)

            viewModel.onAction(SettingsAction.RestoreThemeDefaults)
            assertThat(library.preferences.value).isEqualTo(LibraryPreferences(albumColumns = 3))
        }

    private suspend fun content() = viewModel.uiState.first { it is SettingsUiState.Content } as SettingsUiState.Content

    @Test
    fun `navigation choices and restoring them reach the preferences`() =
        runTest {
            signIn()
            val chosen = NavigationPreferences(trackAction = TrackAction.PlayNext, expandPlayer = true)

            viewModel.onAction(SettingsAction.SetNavigation(chosen))
            assertThat(library.preferences.value.navigation).isEqualTo(chosen)

            viewModel.onAction(SettingsAction.RestoreNavigationDefaults)
            assertThat(library.preferences.value.navigation).isEqualTo(NavigationPreferences())
        }

    @Test
    fun `media lists choices and restoring them reach the preferences`() =
        runTest {
            signIn()
            val chosen =
                MediaListPreferences(
                    hiddenTopButtons = setOf(TopButton.Shuffle),
                    secondTrackInfo = TrackInfo.Codec,
                    swipeLeft = SwipeAction.Disabled,
                )

            viewModel.onAction(SettingsAction.SetMediaLists(chosen))
            assertThat(content().library.mediaLists).isEqualTo(chosen)

            viewModel.onAction(SettingsAction.RestoreMediaListsDefaults)
            assertThat(library.preferences.value.mediaLists).isEqualTo(MediaListPreferences())
        }

    @Test
    fun `the entries dialog opens with the stored entries first, checked`() =
        runTest {
            signIn()
            viewModel.uiState.test {
                skipItems(1)
                viewModel.onAction(SettingsAction.OpenNavEntries)

                val draft = (expectMostRecentItem() as SettingsUiState.Content).entriesDraft!!
                assertThat(draft.order.take(4)).isEqualTo(NavigationPreferences.DEFAULT_ENTRIES)
                assertThat(draft.order).containsExactlyElementsIn(MainNavEntry.entries)
                assertThat(draft.checked).containsExactlyElementsIn(NavigationPreferences.DEFAULT_ENTRIES)
            }
        }

    @Test
    fun `checking, unchecking and moving change the draft, and OK stores it`() =
        runTest {
            signIn()
            viewModel.uiState.test {
                skipItems(1)
                viewModel.onAction(SettingsAction.OpenNavEntries)
                viewModel.onAction(SettingsAction.ToggleNavEntry(MainNavEntry.Downloads))
                viewModel.onAction(SettingsAction.ToggleNavEntry(MainNavEntry.Tracks))
                // Tracks comes after the stored entries: move it right after Home.
                val order = (expectMostRecentItem() as SettingsUiState.Content).entriesDraft!!.order
                viewModel.onAction(SettingsAction.MoveNavEntry(order.indexOf(MainNavEntry.Tracks), 1))

                viewModel.onAction(SettingsAction.ConfirmNavEntries)

                assertThat((expectMostRecentItem() as SettingsUiState.Content).entriesDraft).isNull()
            }
            assertThat(library.preferences.value.navigation.entries)
                .containsExactly(MainNavEntry.Home, MainNavEntry.Tracks, MainNavEntry.Library, MainNavEntry.Search)
                .inOrder()
        }

    @Test
    fun `Cancel drops the draft and keeps the entries`() =
        runTest {
            signIn()
            viewModel.uiState.test {
                skipItems(1)
                viewModel.onAction(SettingsAction.OpenNavEntries)
                viewModel.onAction(SettingsAction.ToggleNavEntry(MainNavEntry.Search))

                viewModel.onAction(SettingsAction.DismissNavEntries)

                assertThat((expectMostRecentItem() as SettingsUiState.Content).entriesDraft).isNull()
            }
            assertThat(library.preferences.value.navigation.entries).isEqualTo(NavigationPreferences.DEFAULT_ENTRIES)
        }

    @Test
    fun `Home cannot be unchecked and no more than six entries can be checked`() {
        val full =
            navEntriesDraft(
                listOf(
                    MainNavEntry.Home,
                    MainNavEntry.Library,
                    MainNavEntry.Albums,
                    MainNavEntry.AlbumArtists,
                    MainNavEntry.Playlists,
                    MainNavEntry.Tracks,
                ),
            )

        assertThat(full.canToggle(MainNavEntry.Home)).isFalse()
        assertThat(full.toggle(MainNavEntry.Home)).isEqualTo(full)
        assertThat(full.canToggle(MainNavEntry.Search)).isFalse()
        assertThat(full.toggle(MainNavEntry.Search)).isEqualTo(full)
        assertThat(full.toggle(MainNavEntry.Tracks).checked).doesNotContain(MainNavEntry.Tracks)
    }

    @Test
    fun `the hub offers Manage offline files only without the Downloads entry`() =
        runTest {
            signIn()
            assertThat(content().showsManageOfflineFiles).isFalse()

            library.setNavigation(NavigationPreferences(entries = listOf(MainNavEntry.Home, MainNavEntry.Search)))

            viewModel.uiState.test {
                assertThat((expectMostRecentItem() as SettingsUiState.Content).showsManageOfflineFiles).isTrue()
            }
        }

    @Test
    fun `now playing choices and restoring them reach the preferences`() =
        runTest {
            signIn()
            val chosen = NowPlayingPreferences(floatingMiniPlayer = false, progress = PlayerProgress.Wavy)

            viewModel.onAction(SettingsAction.SetNowPlaying(chosen))
            assertThat(content().library.nowPlaying).isEqualTo(chosen)

            viewModel.onAction(SettingsAction.RestoreNowPlayingDefaults)
            assertThat(library.preferences.value.nowPlaying).isEqualTo(NowPlayingPreferences())
        }

    @Test
    fun `home order, visibility, reset rows and restore reach the preferences`() =
        runTest {
            signIn()
            val order = HomeSection.entries.reversed()

            viewModel.onAction(SettingsAction.SetHomeOrder(order))
            viewModel.onAction(SettingsAction.SetHomeHidden(emptySet()))
            assertThat(content().library.home).isEqualTo(HomePreferences(order, emptySet()))

            viewModel.onAction(SettingsAction.ResetHomeRows)
            assertThat(library.preferences.value.home).isEqualTo(HomePreferences())

            viewModel.onAction(SettingsAction.SetHomeHidden(setOf(HomeSection.FavoriteAlbums)))
            viewModel.onAction(SettingsAction.RestoreHomeDefaults)
            assertThat(library.preferences.value.home).isEqualTo(HomePreferences())
        }

    @Test
    fun `library page choices apply at once and restoring resets only that page`() =
        runTest {
            signIn()
            library.setAlbumColumns(4)
            val chosen = LibraryPagePreferences(style = LibraryHubStyle.List, columns = 2)

            viewModel.onAction(SettingsAction.SetLibraryPage(chosen))
            assertThat(content().library.libraryPage).isEqualTo(chosen)

            viewModel.onAction(SettingsAction.RestoreLibraryPageDefaults)
            assertThat(library.preferences.value.libraryPage).isEqualTo(LibraryPagePreferences())
            assertThat(library.preferences.value.albumColumns).isEqualTo(4)
        }

    @Test
    fun `the library tiles are organized in a draft and stored only with OK`() =
        runTest {
            signIn()
            val organized =
                listOf(
                    LibraryShortcut.Years,
                    LibraryShortcut.Albums,
                    LibraryShortcut.AlbumArtists,
                    LibraryShortcut.Genres,
                    LibraryShortcut.Playlists,
                    LibraryShortcut.RecordLabels,
                )
            viewModel.uiState.test {
                viewModel.onAction(SettingsAction.OpenLibraryShortcuts)
                viewModel.onAction(SettingsAction.ToggleLibraryShortcut(LibraryShortcut.Tracks))
                viewModel.onAction(SettingsAction.MoveLibraryShortcut(5, 0))
                val draft = (expectMostRecentItem() as SettingsUiState.Content).shortcutsDraft
                assertThat(draft?.entries).isEqualTo(organized)
                assertThat(library.preferences.value.libraryPage.shortcuts).isEqualTo(LibraryShortcut.entries)

                viewModel.onAction(SettingsAction.ConfirmLibraryShortcuts)
                assertThat((expectMostRecentItem() as SettingsUiState.Content).shortcutsDraft).isNull()
            }
            assertThat(library.preferences.value.libraryPage.shortcuts).isEqualTo(organized)
        }

    @Test
    fun `cancel drops the library tiles draft`() =
        runTest {
            signIn()
            viewModel.uiState.test {
                viewModel.onAction(SettingsAction.OpenLibraryShortcuts)
                viewModel.onAction(SettingsAction.ToggleLibraryShortcut(LibraryShortcut.Albums))

                viewModel.onAction(SettingsAction.DismissLibraryShortcuts)

                assertThat((expectMostRecentItem() as SettingsUiState.Content).shortcutsDraft).isNull()
            }
            assertThat(library.preferences.value.libraryPage.shortcuts).isEqualTo(LibraryShortcut.entries)
        }

    @Test
    fun `the last checked tile cannot be unchecked`() {
        val one = OrganizeDraft.of(listOf(LibraryShortcut.Years), LibraryShortcut.entries)

        assertThat(one.canToggle(LibraryShortcut.Years)).isFalse()
        assertThat(one.toggle(LibraryShortcut.Years)).isEqualTo(one)
        assertThat(one.toggle(LibraryShortcut.Albums).entries)
            .containsExactly(LibraryShortcut.Years, LibraryShortcut.Albums)
            .inOrder()
    }

    @Test
    fun `album page choices apply at once and restoring resets only that page`() =
        runTest {
            signIn()
            library.setAlbumColumns(4)
            val chosen = AlbumPagePreferences(headerBackground = AlbumHeaderBackground.None, showRatingBar = true)

            viewModel.onAction(SettingsAction.SetAlbumPage(chosen))
            assertThat(content().library.albumPage).isEqualTo(chosen)

            viewModel.onAction(SettingsAction.RestoreAlbumPageDefaults)
            assertThat(library.preferences.value.albumPage).isEqualTo(AlbumPagePreferences())
            assertThat(library.preferences.value.albumColumns).isEqualTo(4)
        }

    @Test
    fun `the album sections are ordered in a draft, stored with OK and dropped with Cancel`() =
        runTest {
            signIn()
            viewModel.uiState.test {
                viewModel.onAction(SettingsAction.OpenAlbumSections)
                viewModel.onAction(SettingsAction.MoveAlbumSection(2, 0))
                val draft = (expectMostRecentItem() as SettingsUiState.Content).sectionsDraft
                assertThat(draft?.order)
                    .containsExactly(AlbumSection.About, AlbumSection.Tracks, AlbumSection.MoreFromArtist)
                    .inOrder()
                // Every section stays: the switches hide them, not this dialog.
                assertThat(draft?.canToggle(AlbumSection.About)).isFalse()
                assertThat(library.preferences.value.albumPage.sectionOrder).isEqualTo(AlbumSection.entries)

                viewModel.onAction(SettingsAction.ConfirmAlbumSections)
                assertThat((expectMostRecentItem() as SettingsUiState.Content).sectionsDraft).isNull()

                viewModel.onAction(SettingsAction.OpenAlbumSections)
                viewModel.onAction(SettingsAction.MoveAlbumSection(0, 2))
                viewModel.onAction(SettingsAction.DismissAlbumSections)
                assertThat((expectMostRecentItem() as SettingsUiState.Content).sectionsDraft).isNull()
            }
            assertThat(library.preferences.value.albumPage.sectionOrder)
                .containsExactly(AlbumSection.About, AlbumSection.Tracks, AlbumSection.MoreFromArtist)
                .inOrder()
        }

    @Test
    fun `artist page choices apply at once and restoring resets only that page`() =
        runTest {
            signIn()
            val album = AlbumPagePreferences(showAbout = false)
            library.setAlbumPage(album)
            val chosen = ArtistPagePreferences(headerBackground = ArtistHeaderBackground.None, topTracksCount = 30)

            viewModel.onAction(SettingsAction.SetArtistPage(chosen))
            assertThat(content().library.artistPage).isEqualTo(chosen)

            viewModel.onAction(SettingsAction.RestoreArtistPageDefaults)
            assertThat(library.preferences.value.artistPage).isEqualTo(ArtistPagePreferences())
            assertThat(library.preferences.value.albumPage).isEqualTo(album)
        }

    @Test
    fun `the artist sections are ordered in a draft, stored with OK and dropped with Cancel`() =
        runTest {
            signIn()
            viewModel.uiState.test {
                viewModel.onAction(SettingsAction.OpenArtistSections)
                viewModel.onAction(SettingsAction.MoveArtistSection(3, 0))
                val draft = (expectMostRecentItem() as SettingsUiState.Content).artistSectionsDraft
                assertThat(draft?.order)
                    .containsExactly(
                        ArtistSection.About,
                        ArtistSection.Albums,
                        ArtistSection.TopTracks,
                        ArtistSection.AppearsOn,
                    ).inOrder()
                assertThat(draft?.canToggle(ArtistSection.About)).isFalse()

                viewModel.onAction(SettingsAction.ConfirmArtistSections)
                assertThat((expectMostRecentItem() as SettingsUiState.Content).artistSectionsDraft).isNull()

                viewModel.onAction(SettingsAction.OpenArtistSections)
                viewModel.onAction(SettingsAction.MoveArtistSection(0, 3))
                viewModel.onAction(SettingsAction.DismissArtistSections)
                assertThat((expectMostRecentItem() as SettingsUiState.Content).artistSectionsDraft).isNull()
            }
            assertThat(library.preferences.value.artistPage.sectionOrder)
                .containsExactly(
                    ArtistSection.About,
                    ArtistSection.Albums,
                    ArtistSection.TopTracks,
                    ArtistSection.AppearsOn,
                ).inOrder()
        }

    @Test
    fun `genre page choices apply at once and restoring resets only that page`() =
        runTest {
            signIn()
            val artist = ArtistPagePreferences(showAbout = false)
            library.setArtistPage(artist)
            val chosen = GenrePagePreferences(showAlbumArtists = true, dynamicColors = true)

            viewModel.onAction(SettingsAction.SetGenrePage(chosen))
            assertThat(content().library.genrePage).isEqualTo(chosen)

            viewModel.onAction(SettingsAction.RestoreGenrePageDefaults)
            assertThat(library.preferences.value.genrePage).isEqualTo(GenrePagePreferences())
            assertThat(library.preferences.value.artistPage).isEqualTo(artist)
        }

    @Test
    fun `the genre sections are ordered in a draft, stored with OK and dropped with Cancel`() =
        runTest {
            signIn()
            viewModel.uiState.test {
                viewModel.onAction(SettingsAction.OpenGenreSections)
                viewModel.onAction(SettingsAction.MoveGenreSection(3, 0))
                val draft = (expectMostRecentItem() as SettingsUiState.Content).genreSectionsDraft
                assertThat(draft?.order)
                    .containsExactly(
                        GenreSection.TopTracks,
                        GenreSection.Artists,
                        GenreSection.AlbumArtists,
                        GenreSection.Albums,
                    ).inOrder()

                viewModel.onAction(SettingsAction.ConfirmGenreSections)
                assertThat((expectMostRecentItem() as SettingsUiState.Content).genreSectionsDraft).isNull()

                viewModel.onAction(SettingsAction.OpenGenreSections)
                viewModel.onAction(SettingsAction.MoveGenreSection(0, 3))
                viewModel.onAction(SettingsAction.DismissGenreSections)
                assertThat((expectMostRecentItem() as SettingsUiState.Content).genreSectionsDraft).isNull()
            }
            assertThat(
                library.preferences.value.genrePage.sectionOrder
                    .first(),
            ).isEqualTo(GenreSection.TopTracks)
        }

    @Test
    fun `playlist page choices apply at once and restoring resets only that page`() =
        runTest {
            signIn()
            val genre = GenrePagePreferences(showAlbums = false)
            library.setGenrePage(genre)
            val chosen = PlaylistPagePreferences(hiddenButtons = setOf(PlaylistButton.Resume), dynamicColors = true)

            viewModel.onAction(SettingsAction.SetPlaylistPage(chosen))
            assertThat(content().library.playlistPage).isEqualTo(chosen)

            viewModel.onAction(SettingsAction.RestorePlaylistPageDefaults)
            assertThat(library.preferences.value.playlistPage).isEqualTo(PlaylistPagePreferences())
            assertThat(library.preferences.value.genrePage).isEqualTo(genre)
        }

    @Test
    fun `search page choices apply at once and restoring resets only that page`() =
        runTest {
            signIn()
            val playlist = PlaylistPagePreferences(dynamicColors = true)
            library.setPlaylistPage(playlist)
            val chosen = SearchPagePreferences(showFavorite = false, trackAction = TrackAction.PlayNext)

            viewModel.onAction(SettingsAction.SetSearchPage(chosen))
            assertThat(content().library.searchPage).isEqualTo(chosen)

            viewModel.onAction(SettingsAction.RestoreSearchPageDefaults)
            assertThat(library.preferences.value.searchPage).isEqualTo(SearchPagePreferences())
            assertThat(library.preferences.value.playlistPage).isEqualTo(playlist)
        }

    @Test
    fun `advanced choices apply at once and restoring resets only that page`() =
        runTest {
            signIn()
            val search = SearchPagePreferences(showFavorite = false)
            library.setSearchPage(search)
            val chosen = AdvancedPreferences(keepScreenOn = KeepScreenOn.Always, hideStatusBar = true)

            viewModel.onAction(SettingsAction.SetAdvanced(chosen))
            assertThat(content().library.advanced).isEqualTo(chosen)

            viewModel.onAction(SettingsAction.RestoreAdvancedDefaults)
            assertThat(library.preferences.value.advanced).isEqualTo(AdvancedPreferences())
            assertThat(library.preferences.value.searchPage).isEqualTo(search)
        }

    @Test
    fun `decoding and playback behavior apply at once and each restore resets only its page`() =
        runTest {
            signIn()
            val behavior = PlaybackBehavior(circular = false, weightedShuffle = false)

            viewModel.onAction(SettingsAction.SetForceInstantTranscoding(true))
            viewModel.onAction(SettingsAction.SetStreamingQuality(metered = true, StreamingQuality.Kbps192))
            viewModel.onAction(SettingsAction.SetBehavior(behavior))
            assertThat(content().audio.forceInstantTranscoding).isTrue()
            assertThat(content().audio.behavior).isEqualTo(behavior)

            viewModel.onAction(SettingsAction.RestoreDecodingDefaults)
            assertThat(content().audio.forceInstantTranscoding).isFalse()
            assertThat(content().audio.meteredQuality).isEqualTo(StreamingQuality.Kbps256)
            assertThat(content().audio.behavior).isEqualTo(behavior)

            viewModel.onAction(SettingsAction.RestoreBehaviorDefaults)
            assertThat(content().audio.behavior).isEqualTo(PlaybackBehavior())
        }
}
