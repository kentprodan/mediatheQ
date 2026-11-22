package io.github.kentprodan.mediatheq.feature.tv.settings

import com.google.common.truth.Truth.assertThat
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

        assertThat(moveSection(order, video, NextUp, up = true))
            .containsExactly(Shortcuts, NextUp, FavoriteAlbums, ContinueWatching)
            .inOrder()
        assertThat(moveSection(order, order, ContinueWatching, up = false))
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
        assertThat(moveSection(order, order, Shortcuts, up = true)).isEqualTo(order)
        assertThat(moveSection(order, order, NextUp, up = false)).isEqualTo(order)
        assertThat(moveSection(order, listOf(NextUp), Shortcuts, up = false)).isEqualTo(order)
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
}
