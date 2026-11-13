package io.github.kentprodan.mediatheq.feature.onboarding.appmode

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.testing.FakeAppModeRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AppModeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val appMode = FakeAppModeRepository(initial = null)

    @Test
    fun `Both is preselected and nothing is stored before Continue`() {
        val viewModel = AppModeViewModel(appMode)

        assertThat(viewModel.uiState.value.selected).isEqualTo(AppMode.Both)
        assertThat(appMode.stored.value).isNull()
    }

    @Test
    fun `the card picked is stored on Continue`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = AppModeViewModel(appMode)

            viewModel.onAction(AppModeAction.Select(AppMode.Video))
            viewModel.onAction(AppModeAction.Continue)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selected).isEqualTo(AppMode.Video)
            assertThat(appMode.stored.value).isEqualTo(AppMode.Video)
        }
}
