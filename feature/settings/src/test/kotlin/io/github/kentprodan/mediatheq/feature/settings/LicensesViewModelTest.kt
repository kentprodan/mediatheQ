package io.github.kentprodan.mediatheq.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class LicensesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val report = """{"libraries": [], "licenses": {}}"""

    private fun viewModel() = LicensesViewModel({ report }, { "{}" }, mainDispatcherRule.testDispatcher)

    @Test
    fun `before anything is collected the screen is loading`() {
        assertThat(viewModel().uiState.value).isEqualTo(LicensesUiState.Loading)
    }

    @Test
    fun `the report becomes the list, and a row opens and closes its license`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            backgroundScope.launch { viewModel.uiState.collect() }
            assertThat(viewModel.uiState.value).isEqualTo(LicensesUiState.Content(licenseEntries(report, "{}")))

            viewModel.onAction(LicensesAction.Toggle("mediatheq"))
            assertThat((viewModel.uiState.value as LicensesUiState.Content).openId).isEqualTo("mediatheq")

            viewModel.onAction(LicensesAction.Toggle("mediatheq"))
            assertThat((viewModel.uiState.value as LicensesUiState.Content).openId).isNull()
        }
}
