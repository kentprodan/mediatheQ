package io.github.kentprodan.mediatheq.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ChangelogViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val text = "## [Unreleased]\n### Added\n- a\n## [0.1.0] - 2026-10-01\n### Fixed\n- b\n"
    private val viewModel by lazy { ChangelogViewModel({ text }, mainDispatcherRule.testDispatcher) }

    private val content get() = viewModel.uiState.value as ChangelogUiState.Content

    @Test
    fun `the newest version opens first, a tap opens or closes any`() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()
            assertThat(content.versions.map { it.name }).containsExactly("Unreleased", "0.1.0").inOrder()
            assertThat(content.open).containsExactly("Unreleased")

            viewModel.onAction(ChangelogAction.Toggle("0.1.0"))
            viewModel.onAction(ChangelogAction.Toggle("Unreleased"))
            runCurrent()
            assertThat(content.open).containsExactly("0.1.0")

            viewModel.onAction(ChangelogAction.Toggle("0.1.0"))
            runCurrent()
            assertThat(content.open).isEmpty()
        }
}
