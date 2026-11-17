package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoCollection
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.FakeVideoRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TvVideoGridViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val videos = FakeVideoRepository()
    private val sync = FakeLibrarySyncRepository()
    private val savedState = SavedStateHandle()

    private fun movie(
        title: String,
        year: Int,
        watched: Boolean = false,
    ) = Video(MediaId(ServerId("s"), title), VideoKind.Movie, title, year = year, userData = UserData(played = watched))

    private fun TestScope.viewModel(): TvMoviesViewModel {
        val viewModel = TvMoviesViewModel(savedState, sync, videos, mainDispatcherRule.testDispatcher)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TvVideoGridViewModel.content() = uiState.value as TvVideoGridUiState.Content

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `nothing synced is empty, and Sync now syncs`() =
        test {
            val viewModel = viewModel()

            assertThat(viewModel.uiState.value).isEqualTo(TvVideoGridUiState.Empty)
            viewModel.onAction(TvVideoGridAction.Sync)
            runCurrent()
            assertThat(sync.syncCalls).isEqualTo(1)
        }

    @Test
    fun `sort and filters apply, count the whole grid, are kept in the saved state and clear together`() =
        test {
            videos.movies.value =
                listOf(movie("Alien", 1979, watched = true), movie("Brazil", 1985), movie("Heat", 1995))
            val viewModel = viewModel()
            assertThat(
                viewModel.content().posters.map { it.title },
            ).containsExactly("Alien", "Brazil", "Heat").inOrder()

            viewModel.onAction(TvVideoGridAction.SetSort(TvVideoSort.Year))
            viewModel.onAction(TvVideoGridAction.SetWatched(TvWatched.Unwatched))
            runCurrent()

            val content = viewModel.content()
            assertThat(content.posters.map { it.title }).containsExactly("Heat", "Brazil").inOrder()
            assertThat(content.total).isEqualTo(3)
            assertThat(content.decades).containsExactly(1990, 1980, 1970).inOrder()
            assertThat(
                TvMoviesViewModel(savedState, sync, videos, mainDispatcherRule.testDispatcher).kind,
            ).isEqualTo(TvVideoKind.Movies)
            assertThat(savedState.get<TvVideoSort>("sort")).isEqualTo(TvVideoSort.Year)

            viewModel.onAction(TvVideoGridAction.SetDecade(1980))
            runCurrent()
            assertThat(viewModel.content().posters.map { it.title }).containsExactly("Brazil")

            viewModel.onAction(TvVideoGridAction.ClearFilters)
            runCurrent()
            assertThat(viewModel.content().filter).isEqualTo(TvVideoFilter())
            assertThat(viewModel.content().posters).hasSize(3)
        }

    @Test
    fun `a collection shows its name and its movies in its own order first`() =
        test {
            val saga = VideoCollection(MediaId(ServerId("s"), "c1"), "Saga")
            videos.collections.value = listOf(saga)
            videos.collectionMovies.value = mapOf(saga.id to listOf(movie("Zeta", 2001), movie("Alpha", 1999)))
            val viewModel = TvCollectionViewModel(saga.id, savedState, sync, videos, mainDispatcherRule.testDispatcher)
            backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()

            val content = viewModel.content()
            assertThat(content.title).isEqualTo("Saga")
            assertThat(content.sort).isEqualTo(TvVideoSort.Collection)
            assertThat(content.posters.map { it.title }).containsExactly("Zeta", "Alpha").inOrder()
            assertThat(TvVideoKind.Collections.filters).isFalse()
        }
}
