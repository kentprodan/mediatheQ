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

    private fun TestScope.viewModel(): TvVideosViewModel {
        val viewModel =
            TvVideosViewModel(
                TvVideoKind.Movies,
                TvVideoOnly.All,
                savedState,
                sync,
                videos,
                mainDispatcherRule.testDispatcher,
            )
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
            assertThat(viewModel.kind).isEqualTo(TvVideoKind.Movies)
            assertThat(savedState.get<TvVideoSort>("sort")).isEqualTo(TvVideoSort.Year)

            viewModel.onAction(TvVideoGridAction.ToggleDecade(1980))
            runCurrent()
            assertThat(viewModel.content().posters.map { it.title }).containsExactly("Brazil")

            // A second decade adds to the first (#579); choosing it again removes it, Any clears them.
            viewModel.onAction(TvVideoGridAction.ToggleDecade(1990))
            runCurrent()
            assertThat(viewModel.content().posters.map { it.title }).containsExactly("Heat", "Brazil").inOrder()
            viewModel.onAction(TvVideoGridAction.ToggleDecade(1990))
            runCurrent()
            assertThat(viewModel.content().filter.decades).containsExactly(1980)
            viewModel.onAction(TvVideoGridAction.ToggleDecade(null))
            runCurrent()
            assertThat(viewModel.content().filter.decades).isEmpty()
            viewModel.onAction(TvVideoGridAction.ToggleDecade(1980))

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

    private fun TestScope.scoped(only: TvVideoOnly): TvVideosViewModel {
        val viewModel =
            TvVideosViewModel(TvVideoKind.Movies, only, savedState, sync, videos, mainDispatcherRule.testDispatcher)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private val library =
        listOf(
            movie("Alien", 1979).copy(genres = listOf("Horror", "Sci-Fi"), studios = listOf("Brandywine")),
            movie(
                "Brazil",
                1985,
            ).let { it.copy(genres = listOf("Comedy"), userData = it.userData.copy(isFavorite = true)) },
            movie("Heat", 1995).copy(genres = listOf("Crime"), studios = listOf("Warner Bros.")),
        )

    @Test
    fun `the Favorites tab holds only favorites, which Clear filters keeps and the filter count leaves out (#522)`() =
        test {
            videos.movies.value = library
            val viewModel = scoped(TvVideoOnly.Favorites)

            assertThat(viewModel.content().posters.map { it.title }).containsExactly("Brazil")
            viewModel.onAction(TvVideoGridAction.SetWatched(TvWatched.Watched))
            viewModel.onAction(TvVideoGridAction.ClearFilters)
            runCurrent()

            assertThat(viewModel.content().posters.map { it.title }).containsExactly("Brazil")
            assertThat(viewModel.content().filter.active).isEqualTo(0)
            assertThat(viewModel.content().title).isNull()
        }

    @Test
    fun `a genre or studio page holds its posters under its name (#522)`() =
        test {
            videos.movies.value = library

            val genre = scoped(TvVideoOnly.Genre("Sci-Fi")).content()
            assertThat(genre.title).isEqualTo("Sci-Fi")
            assertThat(genre.posters.map { it.title }).containsExactly("Alien")
            val studio = scoped(TvVideoOnly.Studio("Warner Bros.")).content()
            assertThat(studio.title).isEqualTo("Warner Bros.")
            assertThat(studio.posters.map { it.title }).containsExactly("Heat")
        }

    @Test
    fun `the Genres and Studios tabs list a card per name with its count, A to Z (#522)`() =
        test {
            videos.movies.value = library + movie("Ronin", 1998).copy(genres = listOf("Crime"))

            fun TestScope.groups(studios: Boolean): List<TvVideoGroup> {
                val viewModel =
                    TvVideoGroupsViewModel(TvVideoKind.Movies, studios, videos, mainDispatcherRule.testDispatcher)
                backgroundScope.launch { viewModel.groups.collect {} }
                runCurrent()
                return (viewModel.groups.value as TvVideoGroupsUiState.Groups).groups
            }

            assertThat(groups(studios = false).map { it.name to it.count })
                .containsExactly("Comedy" to 1, "Crime" to 2, "Horror" to 1, "Sci-Fi" to 1)
                .inOrder()
            assertThat(groups(studios = true).map { it.name }).containsExactly("Brandywine", "Warner Bros.").inOrder()
        }
}
