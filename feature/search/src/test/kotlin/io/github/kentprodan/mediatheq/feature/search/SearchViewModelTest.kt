package io.github.kentprodan.mediatheq.feature.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.SearchResults
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
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

    // Lazy: viewModelScope must be created after the rule replaced the main dispatcher.
    private val viewModel by lazy { SearchViewModel(library, preferences, playback) }

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val ada = Artist(id("ar"), "Ada Moșoi")
    private val track = Track(id("t1"), "Moșoi", listOf(ada), Duration.ZERO)
    private val album = Album(id("al"), "Moșoi Live", listOf(ada))

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
    fun `a song can be queued next or last`() =
        test {
            viewModel.onAction(SearchAction.PlayNext(track))
            viewModel.onAction(SearchAction.AddToQueue(track))

            assertThat(playback.playedNext).containsExactly(listOf(track))
            assertThat(playback.addedToQueue).containsExactly(listOf(track))
        }

    @Test
    fun `clear empties the recent searches`() =
        test {
            preferences.addRecentSearch("mosoi")

            viewModel.onAction(SearchAction.ClearRecent)

            assertThat(preferences.recentSearches.value).isEmpty()
        }
}
