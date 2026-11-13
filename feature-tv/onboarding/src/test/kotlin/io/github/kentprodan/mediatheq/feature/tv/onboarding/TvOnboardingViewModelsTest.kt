package io.github.kentprodan.mediatheq.feature.tv.onboarding

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.LogoutReason
import io.github.kentprodan.mediatheq.core.data.ServerCheck
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.ServerInfo
import io.github.kentprodan.mediatheq.core.navigation.OnboardingLoginKey
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import io.github.kentprodan.mediatheq.feature.tv.onboarding.login.TvLoginAction
import io.github.kentprodan.mediatheq.feature.tv.onboarding.login.TvLoginError
import io.github.kentprodan.mediatheq.feature.tv.onboarding.login.TvLoginViewModel
import io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect.TvQuickConnectAction
import io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect.TvQuickConnectUiState
import io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect.TvQuickConnectViewModel
import io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect.TvQuickConnectViewModel.Companion.MAX_POLLS
import io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect.TvQuickConnectViewModel.Companion.POLL_INTERVAL_MILLIS
import io.github.kentprodan.mediatheq.feature.tv.onboarding.server.TvServerAction
import io.github.kentprodan.mediatheq.feature.tv.onboarding.server.TvServerError
import io.github.kentprodan.mediatheq.feature.tv.onboarding.server.TvServerViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TvOnboardingViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val repository = FakeSessionRepository()
    private val quickConnect = OnboardingQuickConnectKey("http://nas:8096", "Home")

    // Server

    private fun TestScope.serverError(check: ServerCheck): TvServerError? {
        repository.serverCheck = check
        val viewModel = TvServerViewModel(repository)
        viewModel.onAction(TvServerAction.AddressChanged("nas"))
        viewModel.onAction(TvServerAction.Connect)
        runCurrent()
        return viewModel.uiState.value.error
    }

    @Test
    fun `a supported server goes on to Quick Connect, the TV's first way in`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = TvServerViewModel(repository)

            viewModel.onAction(TvServerAction.AddressChanged("nas:8096"))
            viewModel.onAction(TvServerAction.Connect)
            assertThat(viewModel.uiState.value.checking).isTrue()
            runCurrent()

            assertThat(viewModel.uiState.value.nextKey).isEqualTo(quickConnect)
            viewModel.onAction(TvServerAction.NavigationHandled)
            assertThat(viewModel.uiState.value.nextKey).isNull()
            assertThat(viewModel.uiState.value.address).isEqualTo("nas:8096")
        }

    @Test
    fun `every failed check has its own error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            assertThat(serverError(ServerCheck.InvalidAddress)).isEqualTo(TvServerError.InvalidAddress)
            assertThat(serverError(ServerCheck.CleartextNotAllowed)).isEqualTo(TvServerError.CleartextNotAllowed)
            assertThat(serverError(ServerCheck.Failed(MqError.Network))).isEqualTo(TvServerError.Unreachable)
            assertThat(
                serverError(ServerCheck.Failed(MqError.Unknown(IllegalStateException("html")))),
            ).isEqualTo(TvServerError.NotJellyfin)
            assertThat(serverError(ServerCheck.Failed(MqError.Server(500)))).isEqualTo(TvServerError.Other)
            assertThat(
                serverError(ServerCheck.Unsupported(ServerInfo(ServerId("s"), "Old", "10.8.0"))),
            ).isEqualTo(TvServerError.Unsupported("10.8.0"))
        }

    @Test
    fun `a 401 sign-out shows its notice on the server screen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.session.value = SessionState.LoggedOut(LogoutReason.SessionExpired)
            val viewModel = TvServerViewModel(repository)
            runCurrent()

            assertThat(viewModel.uiState.value.notice).isEqualTo(LogoutReason.SessionExpired)
            viewModel.viewModelScope.cancel()
        }

    // Quick Connect

    private fun quickConnectTest(block: suspend TestScope.(TvQuickConnectViewModel) -> Unit) =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = TvQuickConnectViewModel(quickConnect, repository)
            block(viewModel)
            // Like leaving the screen; otherwise runTest would advance the poll loop to its end.
            viewModel.viewModelScope.cancel()
        }

    private fun TestScope.nextPoll() {
        advanceTimeBy(POLL_INTERVAL_MILLIS)
        runCurrent()
    }

    @Test
    fun `shows a code and signs in once it is approved`() =
        quickConnectTest { viewModel ->
            assertThat(viewModel.uiState.value).isEqualTo(TvQuickConnectUiState.Starting("Home"))
            repository.quickConnectApprovals = listOf(MqResult.Success(false), MqResult.Success(true))
            runCurrent()
            assertThat(viewModel.uiState.value).isEqualTo(TvQuickConnectUiState.Waiting("Home", "123456"))

            nextPoll()
            assertThat(repository.session.value).isInstanceOf(SessionState.LoggedOut::class.java)
            nextPoll()
            assertThat(repository.session.value).isInstanceOf(SessionState.LoggedIn::class.java)
        }

    @Test
    fun `with Quick Connect off the password is the way, and no code is asked for`() =
        quickConnectTest { viewModel ->
            repository.quickConnectEnabled = MqResult.Success(false)
            repository.quickConnectCode = MqResult.Failure(MqError.Server(500))
            runCurrent()

            assertThat(viewModel.uiState.value).isEqualTo(TvQuickConnectUiState.Disabled("Home"))
        }

    @Test
    fun `an expired code, or ten minutes without approval, offers a new one`() =
        quickConnectTest { viewModel ->
            repository.quickConnectApprovals = listOf(MqResult.Failure(MqError.NotFound))
            runCurrent()
            nextPoll()
            assertThat(viewModel.uiState.value).isEqualTo(TvQuickConnectUiState.Expired("Home"))

            repository.quickConnectApprovals = listOf(MqResult.Failure(MqError.Network))
            viewModel.onAction(TvQuickConnectAction.Restart)
            runCurrent()
            assertThat(viewModel.uiState.value).isEqualTo(TvQuickConnectUiState.Waiting("Home", "123456"))
            repeat(MAX_POLLS) { nextPoll() }
            assertThat(viewModel.uiState.value).isEqualTo(TvQuickConnectUiState.Expired("Home"))
        }

    @Test
    fun `a new code while waiting stops the old poll, so the old code can no longer sign in`() =
        quickConnectTest { viewModel ->
            runCurrent()
            repository.quickConnectCode = MqResult.Success(QuickConnectCode("654321", "other"))
            viewModel.onAction(TvQuickConnectAction.Restart)
            repository.quickConnectApprovals = listOf(MqResult.Success(true))
            runCurrent()

            assertThat(viewModel.uiState.value).isEqualTo(TvQuickConnectUiState.Waiting("Home", "654321"))
            assertThat(repository.approvalChecks).isEqualTo(0)
            nextPoll()
            assertThat(repository.approvalChecks).isEqualTo(1)
            assertThat(repository.session.value).isInstanceOf(SessionState.LoggedIn::class.java)
        }

    @Test
    fun `an unreachable server and a failed sign-in are errors`() =
        quickConnectTest { viewModel ->
            repository.quickConnectEnabled = MqResult.Failure(MqError.Network)
            runCurrent()
            assertThat(viewModel.uiState.value).isEqualTo(TvQuickConnectUiState.Error("Home", unreachable = true))

            repository.quickConnectEnabled = MqResult.Success(true)
            repository.quickConnectApprovals = listOf(MqResult.Success(true))
            repository.login = MqResult.Failure(MqError.Server(500))
            viewModel.onAction(TvQuickConnectAction.Restart)
            runCurrent()
            nextPoll()
            assertThat(viewModel.uiState.value).isEqualTo(TvQuickConnectUiState.Error("Home", unreachable = false))
        }

    // Password

    @Test
    fun `a wrong password says so and keeps the fields, a right one signs in`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = TvLoginViewModel(OnboardingLoginKey("http://nas:8096", "Home"), repository)
            viewModel.onAction(TvLoginAction.UserNameChanged(" ana "))
            viewModel.onAction(TvLoginAction.PasswordChanged("wrong"))
            repository.login = MqResult.Failure(MqError.Unauthorized)

            viewModel.onAction(TvLoginAction.Submit)
            assertThat(viewModel.uiState.value.submitting).isTrue()
            runCurrent()

            val state = viewModel.uiState.value
            assertThat(state.error).isEqualTo(TvLoginError.WrongCredentials)
            assertThat(state.userName to state.password).isEqualTo(" ana " to "wrong")
            assertThat(state.submitting).isFalse()

            repository.login = MqResult.Success(Unit)
            viewModel.onAction(TvLoginAction.PasswordChanged("right"))
            assertThat(viewModel.uiState.value.error).isNull()
            viewModel.onAction(TvLoginAction.Submit)
            runCurrent()
            assertThat(repository.session.value).isInstanceOf(SessionState.LoggedIn::class.java)
        }

    @Test
    fun `each sign-in failure has its own error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = TvLoginViewModel(OnboardingLoginKey("http://nas:8096", "Home"), repository)
            viewModel.onAction(TvLoginAction.UserNameChanged("ana"))
            listOf(
                MqError.Network to TvLoginError.Unreachable,
                MqError.Storage to TvLoginError.Storage,
                MqError.Server(500) to TvLoginError.Other,
            ).forEach { (error, expected) ->
                repository.login = MqResult.Failure(error)
                viewModel.onAction(TvLoginAction.Submit)
                runCurrent()
                assertThat(viewModel.uiState.value.error).isEqualTo(expected)
            }
        }
}
