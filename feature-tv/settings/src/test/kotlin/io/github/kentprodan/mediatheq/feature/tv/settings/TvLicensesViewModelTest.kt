package io.github.kentprodan.mediatheq.feature.tv.settings

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.APP_ENTRY
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TvLicensesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val report =
        """
        {"libraries": [{"uniqueId": "androidx.tv:tv-material", "name": "TV Material", "artifactVersion": "1.1.0",
          "licenses": ["Apache-2.0"]}],
         "licenses": {"Apache-2.0": {"name": "Apache License 2.0", "content": "Apache text"}}}
        """.trimIndent()

    @Test
    fun `the report is read once into the app, the copied assets and the libraries`() =
        runTest(mainDispatcherRule.testDispatcher) {
            var reads = 0
            val viewModel =
                TvLicensesViewModel({ report.also { reads++ } }, { "{}" }, mainDispatcherRule.testDispatcher)
            assertThat(viewModel.uiState.value).isEqualTo(TvLicensesUiState.Loading)

            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()

            val entries = (viewModel.uiState.value as TvLicensesUiState.Content).entries
            assertThat(entries.first().id).isEqualTo(APP_ENTRY.id)
            assertThat(entries.last().name).isEqualTo("TV Material")
            assertThat(entries.last().licenseText).isEqualTo("Apache text")
            assertThat(reads).isEqualTo(1)
        }
}
