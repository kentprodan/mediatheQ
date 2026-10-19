package io.github.kentprodan.mediatheq.feature.onboarding.login

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.navigation.OnboardingLoginKey
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSessionRepository()
    private val key = OnboardingLoginKey("http://nas:8096", "Home")

    private fun viewModel() = LoginViewModel(key, repository)

    private fun LoginViewModel.signIn(): LoginUiState {
        onAction(LoginAction.UserNameChanged("Ana"))
        onAction(LoginAction.PasswordChanged("pw"))
        onAction(LoginAction.Submit)
        return uiState.value
    }

    @Test
    fun `starts with an empty form for the server`() {
        assertThat(viewModel().uiState.value).isEqualTo(
            LoginUiState(serverName = "Home", quickConnect = QuickConnectAvailability.Available),
        )
    }

    @Test
    fun `a successful sign-in logs in`() {
        val state = viewModel().signIn()

        assertThat(state.error).isNull()
        assertThat(state.submitting).isFalse()
        assertThat(repository.session.value).isEqualTo(SessionState.LoggedIn("Home", "Ana"))
    }

    @Test
    fun `wrong credentials, unreachable server and storage each have their own error`() {
        mapOf(
            MqError.Unauthorized to LoginError.WrongCredentials,
            MqError.Network to LoginError.Unreachable,
            MqError.Storage to LoginError.Storage,
            MqError.Server(500) to LoginError.Other,
        ).forEach { (error, expected) ->
            repository.login = MqResult.Failure(error)
            assertThat(viewModel().signIn().error).isEqualTo(expected)
        }
    }

    @Test
    fun `typing clears the error`() {
        repository.login = MqResult.Failure(MqError.Unauthorized)
        val viewModel = viewModel()
        viewModel.signIn()

        viewModel.onAction(LoginAction.PasswordChanged("pw2"))

        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `a blank username is not submitted`() {
        val viewModel = viewModel()

        viewModel.onAction(LoginAction.Submit)

        assertThat(repository.session.value).isInstanceOf(SessionState.LoggedOut::class.java)
    }

    @Test
    fun `Quick Connect is disabled when the server turns it off or cannot say`() {
        repository.quickConnectEnabled = MqResult.Success(false)
        assertThat(viewModel().uiState.value.quickConnect).isEqualTo(QuickConnectAvailability.Disabled)

        repository.quickConnectEnabled = MqResult.Failure(MqError.Network)
        assertThat(viewModel().uiState.value.quickConnect).isEqualTo(QuickConnectAvailability.Disabled)
    }

    @Test
    fun `opening Quick Connect navigates once`() {
        val viewModel = viewModel()

        viewModel.onAction(LoginAction.OpenQuickConnect)
        assertThat(viewModel.uiState.value.nextKey).isEqualTo(OnboardingQuickConnectKey("http://nas:8096", "Home"))

        viewModel.onAction(LoginAction.NavigationHandled)
        assertThat(viewModel.uiState.value.nextKey).isNull()
    }
}
