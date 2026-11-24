package io.github.kentprodan.mediatheq.feature.tv.settings

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.TvCardStyle
import io.github.kentprodan.mediatheq.core.data.genreCounts
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.HomeSection.ContinueWatching
import io.github.kentprodan.mediatheq.core.model.HomeSection.FavoriteAlbums
import io.github.kentprodan.mediatheq.core.model.HomeSection.NextUp
import io.github.kentprodan.mediatheq.core.model.HomeSection.Shortcuts
import io.github.kentprodan.mediatheq.core.testing.FakeAppModeRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TvHomeRowsTest {
    private val order = listOf(Shortcuts, ContinueWatching, FavoriteAlbums, NextUp)

    @Test
    fun `a row swaps with its visible neighbor, rows of the other mode keep their places`() {
        // Video only: Shortcuts and FavoriteAlbums are not listed.
        val video = listOf(ContinueWatching, NextUp)

        assertThat(moveRow(order, video, NextUp, up = true))
            .containsExactly(Shortcuts, NextUp, FavoriteAlbums, ContinueWatching)
            .inOrder()
        assertThat(moveRow(order, order, ContinueWatching, up = false))
            .containsExactly(Shortcuts, FavoriteAlbums, ContinueWatching, NextUp)
            .inOrder()
    }

    @Test
    fun `genres are listed the most common first, then by name`() {
        assertThat(genreCounts(listOf(listOf("Drama", "Comedy"), listOf("Drama"), listOf("Action"))).map { it.first })
            .containsExactly("Drama", "Action", "Comedy")
            .inOrder()
    }

    @Test
    fun `at an end, or for a row not listed, nothing moves`() {
        assertThat(moveRow(order, order, Shortcuts, up = true)).isEqualTo(order)
        assertThat(moveRow(order, order, NextUp, up = false)).isEqualTo(order)
        assertThat(moveRow(order, listOf(NextUp), Shortcuts, up = false)).isEqualTo(order)
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `a row is made immersive and back from its menu, and the list shows it (#521)`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val preferences = FakeLibraryPreferencesRepository()
            val viewModel = TvHomeRowsViewModel(preferences, FakeAppModeRepository(initial = AppMode.Both))
            backgroundScope.launch { viewModel.state.collect() }

            viewModel.onAction(TvHomeRowsAction.ToggleImmersive(ContinueWatching))
            runCurrent()
            assertThat(preferences.preferences.value.navigation.tvImmersiveRows).containsExactly(ContinueWatching)
            assertThat(
                viewModel.state.value!!
                    .rows
                    .single { it.section == ContinueWatching }
                    .immersive,
            ).isTrue()

            viewModel.onAction(TvHomeRowsAction.ToggleImmersive(ContinueWatching))
            runCurrent()
            assertThat(preferences.preferences.value.navigation.tvImmersiveRows).isEmpty()
        }

    @Test
    fun `a row's cards per screen is set and back to the default, and the list shows it (#604)`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val preferences = FakeLibraryPreferencesRepository()
            val viewModel = TvHomeRowsViewModel(preferences, FakeAppModeRepository(initial = AppMode.Both))
            backgroundScope.launch { viewModel.state.collect() }

            viewModel.onAction(TvHomeRowsAction.SetPerScreen(ContinueWatching.name, 2))
            runCurrent()
            assertThat(preferences.preferences.value.navigation.tvCardsPerScreen).containsExactly(ContinueWatching, 2)
            assertThat(
                viewModel.state.value!!
                    .rows
                    .single { it.section == ContinueWatching }
                    .perScreen,
            ).isEqualTo(2)

            viewModel.onAction(TvHomeRowsAction.SetPerScreen(ContinueWatching.name, null))
            runCurrent()
            assertThat(preferences.preferences.value.navigation.tvCardsPerScreen).isEmpty()
        }

    @Test
    fun `a genre row joins Home rows, moves among the sections and takes its own design (#621)`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val preferences = FakeLibraryPreferencesRepository()
            val viewModel = TvHomeRowsViewModel(preferences, FakeAppModeRepository(initial = AppMode.Video))
            backgroundScope.launch { viewModel.state.collect() }

            viewModel.onAction(TvHomeRowsAction.ToggleGenre("Drama"))
            runCurrent()
            val ids =
                viewModel.state.value!!
                    .rows
                    .map { it.id }
            assertThat(ids.last()).isEqualTo("genre:Drama")

            viewModel.onAction(TvHomeRowsAction.Move("genre:Drama", up = true))
            runCurrent()
            assertThat(
                viewModel.state.value!!
                    .rows
                    .map { it.id },
            ).containsExactlyElementsIn(ids.dropLast(2) + "genre:Drama" + ids[ids.size - 2])
                .inOrder()

            viewModel.onAction(TvHomeRowsAction.SetCardStyle("genre:Drama", TvCardStyle.WideClassic))
            runCurrent()
            assertThat(preferences.preferences.value.navigation.tvGenreCardStyles)
                .containsExactly("Drama", TvCardStyle.WideClassic)
            assertThat(
                viewModel.state.value!!
                    .rows
                    .single { it.genre == "Drama" }
                    .cardStyle,
            ).isEqualTo(TvCardStyle.WideClassic)
        }
}
