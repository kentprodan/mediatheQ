package io.github.kentprodan.mediatheq.feature.library

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.LibraryHubStyle
import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
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

    @Test
    fun `the hub shows the library page settings and follows their changes at once`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = LibraryHubViewModel(preferences)
            assertThat(viewModel.uiState.value).isEqualTo(LibraryHubUiState.Loading)
            backgroundScope.launch { viewModel.uiState.collect() }

            assertThat(viewModel.uiState.value).isEqualTo(LibraryHubUiState.Content(LibraryPagePreferences()))

            val page = LibraryPagePreferences(listOf(LibraryShortcut.Years), style = LibraryHubStyle.List, columns = 2)
            preferences.setLibraryPage(page)

            assertThat(viewModel.uiState.value).isEqualTo(LibraryHubUiState.Content(page))
        }
}
