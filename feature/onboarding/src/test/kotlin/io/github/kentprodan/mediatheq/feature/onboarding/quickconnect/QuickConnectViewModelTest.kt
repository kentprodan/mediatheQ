package io.github.kentprodan.mediatheq.feature.onboarding.quickconnect

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import io.github.kentprodan.mediatheq.feature.onboarding.quickconnect.QuickConnectViewModel.Companion.MAX_POLLS
import io.github.kentprodan.mediatheq.feature.onboarding.quickconnect.QuickConnectViewModel.Companion.POLL_INTERVAL_MILLIS
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class QuickConnectViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val repository = FakeSessionRepository()
    private val key = OnboardingQuickConnectKey("http://nas:8096", "Home")

    private fun test(block: suspend TestScope.(QuickConnectViewModel) -> Unit) =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = QuickConnectViewModel(key, repository)
            block(viewModel)
            // Like leaving the screen; otherwise runTest would advance the endless poll loop forever.
            viewModel.viewModelScope.cancel()
        }

    private fun TestScope.nextPoll() {
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
    }

    @Test
    fun `starts by asking for a code`() =
        test { viewModel ->
            assertThat(viewModel.uiState.value).isEqualTo(QuickConnectUiState.Starting("Home"))

            runCurrent()

            assertThat(viewModel.uiState.value).isEqualTo(QuickConnectUiState.Waiting("Home", "123456"))
        }

    @Test
    fun `polls on a fixed interval and logs in once approved`() =
        test {
            repository.quickConnectApprovals = listOf(MqResult.Success(false), MqResult.Success(true))
            runCurrent()

            advanceTimeBy(POLL_INTERVAL_MILLIS - 1)
            runCurrent()
            assertThat(repository.approvalChecks).isEqualTo(0)

            nextPoll()
            assertThat(repository.approvalChecks).isEqualTo(1)
            assertThat(repository.session.value).isInstanceOf(SessionState.LoggedOut::class.java)

            nextPoll()
            assertThat(repository.session.value).isEqualTo(SessionState.LoggedIn("Home", "Ana"))

            nextPoll()
            assertThat(repository.approvalChecks).isEqualTo(2)
        }

    @Test
    fun `an expired code offers a new one`() =
        test { viewModel ->
            repository.quickConnectApprovals = listOf(MqResult.Failure(MqError.NotFound))
            runCurrent()
            nextPoll()

            assertThat(viewModel.uiState.value).isEqualTo(QuickConnectUiState.Expired("Home"))

            viewModel.onAction(QuickConnectAction.Restart)
            runCurrent()
            assertThat(viewModel.uiState.value).isEqualTo(QuickConnectUiState.Waiting("Home", "123456"))
        }

    @Test
    fun `a network blip keeps waiting`() =
        test { viewModel ->
            repository.quickConnectApprovals = listOf(MqResult.Failure(MqError.Network), MqResult.Success(false))
            runCurrent()
            nextPoll()
            nextPoll()

            assertThat(viewModel.uiState.value).isEqualTo(QuickConnectUiState.Waiting("Home", "123456"))
            assertThat(repository.approvalChecks).isEqualTo(2)
        }

    @Test
    fun `polling stops after ten minutes and offers a new code`() =
        test { viewModel ->
            repository.quickConnectApprovals = listOf(MqResult.Failure(MqError.Network))
            runCurrent()

            repeat(MAX_POLLS) { nextPoll() }

            assertThat(viewModel.uiState.value).isEqualTo(QuickConnectUiState.Expired("Home"))
            nextPoll()
            assertThat(repository.approvalChecks).isEqualTo(MAX_POLLS)
        }

    @Test
    fun `an unreachable server when starting is an error`() =
        test { viewModel ->
            repository.quickConnectCode = MqResult.Failure(MqError.Network)
            runCurrent()

            assertThat(viewModel.uiState.value).isEqualTo(QuickConnectUiState.Error("Home", unreachable = true))
        }

    @Test
    fun `a failed login after approval is an error`() =
        test { viewModel ->
            repository.quickConnectApprovals = listOf(MqResult.Success(true))
            repository.login = MqResult.Failure(MqError.Server(500))
            runCurrent()
            nextPoll()

            assertThat(viewModel.uiState.value).isEqualTo(QuickConnectUiState.Error("Home", unreachable = false))
        }
}
