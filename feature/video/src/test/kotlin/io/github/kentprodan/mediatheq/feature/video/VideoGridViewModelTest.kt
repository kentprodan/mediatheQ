package io.github.kentprodan.mediatheq.feature.video

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeVideoRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class VideoGridViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakeLibraryPreferencesRepository()
    private val videos = FakeVideoRepository()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val watched = Video(id("m1"), VideoKind.Movie, "Arrival", year = 2016, userData = UserData(played = true))
    private val fresh = Video(id("m2"), VideoKind.Movie, "Dune", year = 2021)

    @Test
    fun `movies load as posters in the album columns plus one`() =
        runTest(mainDispatcherRule.testDispatcher) {
            preferences.preferences.value = LibraryPreferences(albumColumns = 3)
            videos.movies.value = listOf(watched, fresh)
            val viewModel = MoviesViewModel(SavedStateHandle(), preferences, videos)
            assertThat(viewModel.uiState.value).isEqualTo(VideoGridUiState.Loading)
            backgroundScope.launch { viewModel.uiState.collect() }

            assertThat(viewModel.uiState.value).isEqualTo(
                VideoGridUiState.Content(
                    listOf(
                        PosterItem(id("m1"), "Arrival", "2016", null, watched = true),
                        PosterItem(id("m2"), "Dune", "2021", null, watched = false),
                    ),
                    VideoSort.Name,
                    unwatched = false,
                    columns = 4,
                ),
            )
        }

    @Test
    fun `nothing synced is content without items`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = MoviesViewModel(SavedStateHandle(), preferences, videos)
            backgroundScope.launch { viewModel.uiState.collect() }

            assertThat((viewModel.uiState.value as VideoGridUiState.Content).items).isEmpty()
        }

    @Test
    fun `sort and the unwatched filter ask again and survive in the saved state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            videos.movies.value = listOf(watched, fresh)
            val saved = SavedStateHandle()
            val viewModel = MoviesViewModel(saved, preferences, videos)
            backgroundScope.launch { viewModel.uiState.collect() }

            viewModel.onAction(VideoGridAction.SetSort(VideoSort.Year))
            viewModel.onAction(VideoGridAction.SetUnwatched(true))

            val state = viewModel.uiState.value as VideoGridUiState.Content
            assertThat(state.sort).isEqualTo(VideoSort.Year)
            assertThat(state.items.map { it.title }).containsExactly("Dune")
            assertThat(videos.requests.last()).isEqualTo("movies:Year:true")
            val again = MoviesViewModel(saved, preferences, videos)
            backgroundScope.launch { again.uiState.collect() }
            assertThat((again.uiState.value as VideoGridUiState.Content).sort).isEqualTo(VideoSort.Year)
        }

    @Test
    fun `a failed read is an error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            videos.failure = IllegalStateException("disk")
            val viewModel = SeriesListViewModel(SavedStateHandle(), preferences, videos)
            backgroundScope.launch { viewModel.uiState.collect() }

            assertThat(viewModel.uiState.value).isEqualTo(VideoGridUiState.Error)
        }

    @Test
    fun `series show their unwatched episodes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            videos.series.value =
                listOf(Series(id("sr1"), "Dark", year = 2017, userData = UserData(unplayedCount = 12)))
            val viewModel = SeriesListViewModel(SavedStateHandle(), preferences, videos)
            backgroundScope.launch { viewModel.uiState.collect() }

            val item = (viewModel.uiState.value as VideoGridUiState.Content).items.single()
            assertThat(item.unplayedCount).isEqualTo(12)
            assertThat(item.subtitle).isEqualTo("2017")
            assertThat(videos.requests).containsExactly("series:Name:false")
        }
}
