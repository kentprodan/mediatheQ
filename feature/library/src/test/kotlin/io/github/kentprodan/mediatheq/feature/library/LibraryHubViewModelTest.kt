package io.github.kentprodan.mediatheq.feature.library

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.LibraryHubStyle
import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.testing.FakeAppModeRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeVideoRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class LibraryHubViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakeLibraryPreferencesRepository()
    private val videos = FakeVideoRepository()
    private val appMode = FakeAppModeRepository(AppMode.Both)
    private val music = LibraryShortcut.entries - LibraryShortcut.Movies - LibraryShortcut.Series

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private fun content(viewModel: LibraryHubViewModel) = viewModel.uiState.value as LibraryHubUiState.Content

    @Test
    fun `the hub shows the library page settings and follows their changes at once`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = LibraryHubViewModel(preferences, videos, appMode)
            assertThat(viewModel.uiState.value).isEqualTo(LibraryHubUiState.Loading)
            backgroundScope.launch { viewModel.uiState.collect() }

            assertThat(content(viewModel).page).isEqualTo(LibraryPagePreferences(music))

            val page = LibraryPagePreferences(listOf(LibraryShortcut.Years), style = LibraryHubStyle.List, columns = 2)
            preferences.setLibraryPage(page)

            assertThat(content(viewModel).page).isEqualTo(page)
        }

    @Test
    fun `the video tiles show with their counts once something is synced`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = LibraryHubViewModel(preferences, videos, appMode)
            backgroundScope.launch { viewModel.uiState.collect() }

            videos.movies.value = listOf(Video(id("m1"), VideoKind.Movie, "A"), Video(id("m2"), VideoKind.Movie, "B"))

            assertThat(
                content(viewModel).page.shortcuts,
            ).containsExactlyElementsIn(music + LibraryShortcut.Movies).inOrder()
            assertThat(content(viewModel).counts[LibraryShortcut.Movies]).isEqualTo(2)

            videos.series.value = listOf(Series(id("sr1"), "S"))

            assertThat(content(viewModel).page.shortcuts.takeLast(2))
                .containsExactly(LibraryShortcut.Movies, LibraryShortcut.Series)
                .inOrder()
        }

    @Test
    fun `without video on the server there are no video tiles and no counts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val noVideo = FakeVideoRepository(hasVideo = false)
            noVideo.movies.value = listOf(Video(id("m1"), VideoKind.Movie, "A"))
            val viewModel =
                LibraryHubViewModel(preferences, noVideo, FakeAppModeRepository(AppMode.Both, videoAvailable = false))
            backgroundScope.launch { viewModel.uiState.collect() }

            assertThat(content(viewModel).page.shortcuts).containsExactlyElementsIn(music)
            assertThat(content(viewModel).counts).isEmpty()
        }

    @Test
    fun `a hub of only empty video tiles falls back to the music tiles`() =
        runTest(mainDispatcherRule.testDispatcher) {
            preferences.setLibraryPage(LibraryPagePreferences(listOf(LibraryShortcut.Movies)))
            val viewModel = LibraryHubViewModel(preferences, videos, appMode)
            backgroundScope.launch { viewModel.uiState.collect() }

            assertThat(content(viewModel).page.shortcuts).containsExactlyElementsIn(music)
        }

    @Test
    fun `Video mode shows only Movies and Series, Music mode no video tile`() =
        runTest(mainDispatcherRule.testDispatcher) {
            videos.movies.value = listOf(Video(id("m1"), VideoKind.Movie, "A"))
            videos.series.value = listOf(Series(id("sr1"), "S"))
            appMode.stored.value = AppMode.Video
            val viewModel = LibraryHubViewModel(preferences, videos, appMode)
            backgroundScope.launch { viewModel.uiState.collect() }

            assertThat(content(viewModel).page.shortcuts)
                .containsExactly(LibraryShortcut.Movies, LibraryShortcut.Series)
                .inOrder()

            appMode.stored.value = AppMode.Music
            assertThat(content(viewModel).page.shortcuts).containsExactlyElementsIn(music)
        }

    @Test
    fun `Video mode before the first video sync still shows Movies and Series`() =
        runTest(mainDispatcherRule.testDispatcher) {
            appMode.stored.value = AppMode.Video
            val viewModel = LibraryHubViewModel(preferences, videos, appMode)
            backgroundScope.launch { viewModel.uiState.collect() }

            assertThat(content(viewModel).page.shortcuts)
                .containsExactly(LibraryShortcut.Movies, LibraryShortcut.Series)
                .inOrder()
        }
}
