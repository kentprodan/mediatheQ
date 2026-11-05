package io.github.kentprodan.mediatheq.feature.genre

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection.PlayCall
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class GenreViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val preferences = FakeLibraryPreferencesRepository()
    private val playback = FakePlaybackConnection()
    private val trackTaps by lazy { TrackTaps(playback, preferences) }

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val genreId = id("g-rock")
    private val genre = Genre(genreId, "Rock", albumCount = 1, trackCount = 12)
    private val artist = Artist(id("ar1"), "Subcarpați")
    private val album = Album(id("al1"), "Satelit", listOf(artist))
    private val tracks = List(12) { Track(id("t$it"), "Track $it", listOf(artist), 3.minutes) }

    private fun withGenre() {
        library.genres.value = listOf(genre)
        library.genreAlbums.value = mapOf(genreId to listOf(album))
        library.genreArtists.value = mapOf(genreId to listOf(artist))
        library.genreTracks.value = mapOf(genreId to tracks)
    }

    private fun newViewModel() = GenreViewModel(genreId, library, preferences, playback, trackTaps)

    private fun TestScope.viewModel(): GenreViewModel {
        val viewModel = newViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private fun GenreViewModel.content() = uiState.value as GenreUiState.Content

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `before the first read the page is loading`() =
        test {
            withGenre()

            assertThat(newViewModel().uiState.value).isEqualTo(GenreUiState.Loading)
        }

    @Test
    fun `the page shows the albums, artists and first ten tracks, then all of them`() =
        test {
            withGenre()
            val viewModel = viewModel()

            assertThat(viewModel.content().albums).containsExactly(album)
            assertThat(viewModel.content().artists).containsExactly(artist)
            assertThat(viewModel.content().shownTracks).hasSize(GENRE_TOP_TRACKS)

            viewModel.onAction(GenreAction.ShowAllTracks)
            assertThat(viewModel.content().shownTracks).isEqualTo(tracks)
        }

    @Test
    fun `a genre nothing carries, or nothing offline, is not found`() =
        test {
            assertThat(viewModel().uiState.value).isEqualTo(GenreUiState.NotFound)
        }

    @Test
    fun `play and shuffle send every track of the genre`() =
        test {
            withGenre()
            val viewModel = viewModel()

            viewModel.onAction(GenreAction.Play)
            viewModel.onAction(GenreAction.Shuffle)

            assertThat(playback.plays).containsExactly(PlayCall(tracks, 0, false), PlayCall(tracks, 0, true)).inOrder()
        }

    @Test
    fun `a tapped track plays the tracks of the genre from it`() =
        test {
            withGenre()
            val viewModel = viewModel()

            viewModel.onAction(GenreAction.PlayTrack(tracks[3]))

            assertThat(playback.plays).containsExactly(PlayCall(tracks, 3, false))
        }
}
