package io.github.kentprodan.mediatheq.feature.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.SearchPagePreferences
import io.github.kentprodan.mediatheq.core.data.SearchSort
import io.github.kentprodan.mediatheq.core.data.SwipeAction
import io.github.kentprodan.mediatheq.core.data.TrackAction
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.SearchResults
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.TrackCommands
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakePlaylistRepository
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration

class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val preferences = FakeLibraryPreferencesRepository()
    private val playback = FakePlaybackConnection()
    private val trackTaps by lazy { TrackTaps(playback, preferences) }
    private val commands by lazy { TrackCommands(playback, userData, downloads, preferences) }
    private val userData = FakeUserDataRepository()

    // Lazy: viewModelScope must be created after the rule replaced the main dispatcher.
    private val downloads = FakeDownloadRepository()
    private val playlists = FakePlaylistRepository()
    private val viewModel by lazy {
        SearchViewModel(library, preferences, playback, trackTaps, commands, userData, downloads, playlists)
    }

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val ada = Artist(id("ar"), "Ada MoÈ™oi")
    private val track = Track(id("t1"), "MoÈ™oi", listOf(ada), Duration.ZERO)
    private val album = Album(id("al"), "MoÈ™oi Live", listOf(ada))

    init {
        library.albumArtists.value = listOf(ada)
        library.tracks.value = listOf(track)
        library.albums.value = listOf(album)
    }

    // The ViewModel runs on the main test dispatcher; sharing its scheduler lets the test move virtual time.
    private fun test(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) =
        runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `with nothing typed, the recent searches show at once`() =
        test {
            preferences.addRecentSearch("mosoi")

            viewModel.uiState.test {
                assertThat(awaitItem()).isEqualTo(SearchUiState.Recent(listOf("mosoi")))
            }
            assertThat(library.searches).isEmpty()
        }

    @Test
    fun `before anything is collected the screen is loading`() =
        test {
            assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Loading)
        }

    @Test
    fun `a query of only spaces shows the recent searches and never searches`() =
        test {
            viewModel.uiState.test {
                skipItems(1) // the recent searches
                viewModel.onAction(SearchAction.QueryChanged("mo"))
                advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
                skipItems(1)

                viewModel.onAction(SearchAction.QueryChanged("   "))

                assertThat(awaitItem()).isEqualTo(SearchUiState.Recent(emptyList()))
                advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
                assertThat(library.searches).containsExactly("mo")
            }
        }

    @Test
    fun `typing searches once after a short pause, with the last text only`() =
        test {
            viewModel.uiState.test {
                assertThat(awaitItem()).isEqualTo(SearchUiState.Recent(emptyList()))

                viewModel.onAction(SearchAction.QueryChanged("m"))
                advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS - 10)
                viewModel.onAction(SearchAction.QueryChanged("mo"))
                advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS - 10)
                assertThat(library.searches).isEmpty()

                advanceTimeBy(11)
                assertThat(
                    awaitItem(),
                ).isEqualTo(SearchUiState.Results(SearchResults(listOf(ada), listOf(album), listOf(track))))
                assertThat(library.searches).containsExactly("mo")
            }
        }

    @Test
    fun `clearing the field brings the recent searches back without waiting`() =
        test {
            viewModel.uiState.test {
                skipItems(1) // the recent searches
                viewModel.onAction(SearchAction.QueryChanged("mo"))
                advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
                skipItems(1)

                viewModel.onAction(SearchAction.QueryChanged(""))

                assertThat(awaitItem()).isEqualTo(SearchUiState.Recent(emptyList()))
            }
        }

    @Test
    fun `a query that matches nothing says so`() =
        test {
            viewModel.uiState.test {
                skipItems(1) // the recent searches
                viewModel.onAction(SearchAction.QueryChanged(" zzz "))
                advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)

                assertThat(awaitItem()).isEqualTo(SearchUiState.NoResults("zzz"))
            }
        }

    @Test
    fun `turning offline mode on or off runs the query again and says what was searched`() =
        test {
            viewModel.uiState.test {
                skipItems(1) // the recent searches
                viewModel.onAction(SearchAction.QueryChanged("zzz"))
                advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
                assertThat(awaitItem()).isEqualTo(SearchUiState.NoResults("zzz"))

                library.offline.value = true
                advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)

                assertThat(awaitItem()).isEqualTo(SearchUiState.NoResults("zzz", offline = true))
                assertThat(library.searches).containsExactly("zzz", "zzz")
            }
        }

    @Test
    fun `a failed search shows the error and retry runs it again`() =
        test {
            library.searchError = MqError.Storage
            viewModel.uiState.test {
                skipItems(1) // the recent searches
                viewModel.onAction(SearchAction.QueryChanged("mo"))
                advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
                assertThat(awaitItem()).isEqualTo(SearchUiState.Error(MqError.Storage))

                library.searchError = null
                viewModel.onAction(SearchAction.Retry)
                advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)

                assertThat(awaitItem()).isInstanceOf(SearchUiState.Results::class.java)
                assertThat(library.searches).containsExactly("mo", "mo")
            }
        }

    @Test
    fun `submitting or opening a result keeps the query as a recent search`() =
        test {
            viewModel.onAction(SearchAction.QueryChanged("mosoi"))
            viewModel.onAction(SearchAction.Submit)
            viewModel.onAction(SearchAction.QueryChanged("ada"))
            viewModel.onAction(SearchAction.ResultOpened)

            assertThat(preferences.recentSearches.value).containsExactly("ada", "mosoi").inOrder()
        }

    @Test
    fun `tapping a song plays only that song and keeps the query`() =
        test {
            viewModel.onAction(SearchAction.QueryChanged("mo"))

            viewModel.onAction(SearchAction.PlayTrack(track))

            assertThat(playback.plays.single()).isEqualTo(FakePlaybackConnection.PlayCall(listOf(track), 0, false))
            assertThat(preferences.recentSearches.value).containsExactly("mo")
        }

    @Test
    fun `a song can be downloaded`() =
        test {
            viewModel.onAction(SearchAction.Download(track))

            assertThat(downloads.downloads.value.map { it.track }).containsExactly(track)
        }

    @Test
    fun `a song can be queued next or last`() =
        test {
            viewModel.onAction(SearchAction.PlayNext(track))
            viewModel.onAction(SearchAction.AddToQueue(track))

            assertThat(playback.playedNext).containsExactly(listOf(track))
            assertThat(playback.addedToQueue).containsExactly(listOf(track))
        }

    @Test
    fun `a song menu favorites the track and the results are read again`() =
        test {
            viewModel.uiState.test {
                skipItems(1) // the recent searches
                viewModel.onAction(SearchAction.QueryChanged("mo"))
                advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
                skipItems(1)

                viewModel.onAction(SearchAction.SetFavorite(track, true))
                advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)

                assertThat(userData.calls).containsExactly("favorite:${track.id.itemId}:true")
                assertThat(library.searches).containsExactly("mo", "mo")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clear empties the recent searches`() =
        test {
            preferences.addRecentSearch("mosoi")

            viewModel.onAction(SearchAction.ClearRecent)

            assertThat(preferences.recentSearches.value).isEmpty()
        }

    @Test
    fun `selected results act by their tracks in the order shown, and a new query drops the selection`() =
        test {
            backgroundScope.launch { viewModel.uiState.collect() }
            viewModel.onAction(SearchAction.QueryChanged("mo"))
            advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
            val other = Track(id("t2"), "Other", listOf(ada), Duration.ZERO, albumId = album.id)
            library.tracks.value = listOf(track, other)

            viewModel.onAction(SearchAction.ToggleSelection(track.id))
            viewModel.onAction(SearchAction.ToggleSelection(album.id))
            assertThat((viewModel.uiState.value as SearchUiState.Results).selection.size).isEqualTo(2)
            viewModel.onAction(SearchAction.OnSelection(SelectionCommand.PlayNext))

            // The album (shown before songs) first, then the song.
            assertThat(playback.playedNext.single().map { it.id.itemId }).containsExactly("t2", "t1").inOrder()

            viewModel.onAction(SearchAction.ToggleSelection(track.id))
            viewModel.onAction(SearchAction.QueryChanged("mos"))
            assertThat((viewModel.uiState.value as SearchUiState.Results).selection.isActive).isFalse()
        }

    @Test
    fun `an album and one of its songs selected give the song once, and the album downloads as an album`() =
        test {
            backgroundScope.launch { viewModel.uiState.collect() }
            val inAlbum = track.copy(albumId = album.id)
            library.tracks.value = listOf(inAlbum)
            viewModel.onAction(SearchAction.QueryChanged("mo"))
            advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)

            viewModel.onAction(SearchAction.ToggleSelection(album.id))
            viewModel.onAction(SearchAction.ToggleSelection(inAlbum.id))
            viewModel.onAction(SearchAction.OnSelection(SelectionCommand.Play))
            assertThat(playback.plays.single().queue).containsExactly(inAlbum)

            viewModel.onAction(SearchAction.ToggleSelection(album.id))
            viewModel.onAction(SearchAction.OnSelection(SelectionCommand.Download))
            assertThat(
                downloads.downloads.value
                    .single()
                    .origins,
            ).containsExactly(DownloadOrigin.Album(album.id))
        }

    @Test
    fun `a queue swipe does not search again, a favorite swipe does`() =
        test {
            backgroundScope.launch { viewModel.uiState.collect() }
            viewModel.onAction(SearchAction.QueryChanged("mo"))
            advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)

            viewModel.onAction(SearchAction.SwipeTrack(track, right = true))
            advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
            assertThat(library.searches).containsExactly("mo")

            preferences.setMediaLists(MediaListPreferences(swipeRight = SwipeAction.ToggleFavorite))
            viewModel.onAction(SearchAction.SwipeTrack(track, right = true))
            advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
            assertThat(library.searches).containsExactly("mo", "mo")
        }

    @Test
    fun `a swipe runs its action and a rating rates the track`() =
        test {
            viewModel.onAction(SearchAction.SwipeTrack(track, right = false))
            viewModel.onAction(SearchAction.RateTrack(track, 4))

            assertThat(playback.playedNext).containsExactly(listOf(track))
            assertThat(userData.calls).containsExactly("rating:${track.id.itemId}:4")
        }

    private val many = (1..25).map { Track(id("m$it"), "Mo $it", listOf(ada), Duration.ZERO) }

    private suspend fun kotlinx.coroutines.test.TestScope.searchMany(): SearchViewModel {
        library.tracks.value = many
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.onAction(SearchAction.QueryChanged("mo"))
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
        return viewModel
    }

    private val SearchViewModel.results get() = uiState.value as SearchUiState.Results

    @Test
    fun `All shows 20 songs, the Tracks chip all of them, and a favorite keeps the chip`() =
        test {
            val viewModel = searchMany()
            assertThat(viewModel.results.shown.tracks).hasSize(20)
            assertThat(viewModel.results.shown.artists).containsExactly(ada)

            viewModel.onAction(SearchAction.SelectFilter(SearchFilter.Tracks))
            assertThat(viewModel.results.shown.tracks).hasSize(25)
            assertThat(viewModel.results.shown.artists).isEmpty()

            viewModel.onAction(SearchAction.SetFavorite(many[0], true))
            advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
            assertThat(library.searches).containsExactly("mo", "mo")
            assertThat(viewModel.results.filter).isEqualTo(SearchFilter.Tracks)
        }

    @Test
    fun `a chip with nothing found shows All, and comes back when the next query has its kind`() =
        test {
            val viewModel = searchMany()
            viewModel.onAction(SearchAction.SelectFilter(SearchFilter.Albums))
            assertThat(viewModel.results.filter).isEqualTo(SearchFilter.Albums)

            viewModel.onAction(SearchAction.QueryChanged("mo 1"))
            advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
            assertThat(viewModel.results.filter).isEqualTo(SearchFilter.All)
            assertThat(viewModel.results.chosenFilter).isEqualTo(SearchFilter.Albums)

            viewModel.onAction(SearchAction.QueryChanged("mo"))
            advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
            assertThat(viewModel.results.filter).isEqualTo(SearchFilter.Albums)
        }

    @Test
    fun `on a chip, Select all and the selection act only on what it shows`() =
        test {
            val viewModel = searchMany()
            viewModel.onAction(SearchAction.ToggleSelection(ada.id))
            viewModel.onAction(SearchAction.SelectFilter(SearchFilter.Tracks))
            assertThat(viewModel.results.selection.isActive).isFalse()

            viewModel.onAction(SearchAction.SelectAll)
            assertThat(viewModel.results.selection.size).isEqualTo(25)
            viewModel.onAction(SearchAction.OnSelection(SelectionCommand.AddToQueue))

            assertThat(playback.addedToQueue.single()).isEqualTo(many.sortedBy { it.title.lowercase() })
        }

    @Test
    fun `a song tap follows Same as default, so Play makes the shown songs the queue`() =
        test {
            val viewModel = searchMany()
            viewModel.onAction(SearchAction.SelectFilter(SearchFilter.Tracks))
            preferences.setSearchPage(SearchPagePreferences(sort = SearchSort.Relevance))

            viewModel.onAction(SearchAction.PlayTrack(many[2]))

            assertThat(playback.plays.single()).isEqualTo(FakePlaybackConnection.PlayCall(many, 2, false))
        }

    @Test
    fun `a song tap follows the Search page's own track action`() =
        test {
            val viewModel = searchMany()
            preferences.setNavigation(NavigationPreferences(trackAction = TrackAction.Play))
            preferences.setSearchPage(SearchPagePreferences(trackAction = TrackAction.PlayOnlyThis))

            viewModel.onAction(SearchAction.PlayTrack(many[2]))

            assertThat(playback.plays.single()).isEqualTo(FakePlaybackConnection.PlayCall(listOf(many[2]), 0, false))
        }

    @Test
    fun `the Search page settings apply at once`() =
        test {
            val viewModel = searchMany()
            viewModel.onAction(SearchAction.SelectFilter(SearchFilter.Tracks))
            val relevance = viewModel.results.shown.tracks

            preferences.setSearchPage(SearchPagePreferences(showFavorite = false, sort = SearchSort.Relevance))

            assertThat(viewModel.results.page.showFavorite).isFalse()
            assertThat(viewModel.results.shown.tracks).isEqualTo(many)
            assertThat(relevance).isNotEqualTo(many)
        }
}
