package io.github.kentprodan.mediatheq.feature.onboarding.server

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.LogoutReason
import io.github.kentprodan.mediatheq.core.data.ServerCheck
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.ServerInfo
import io.github.kentprodan.mediatheq.core.navigation.OnboardingLoginKey
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test

class ServerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSessionRepository()

    private fun errorFor(check: ServerCheck): ServerError? {
        repository.serverCheck = check
        val viewModel = ServerViewModel(repository)
        viewModel.onAction(ServerAction.AddressChanged("nas"))
        viewModel.onAction(ServerAction.Connect)
        return viewModel.uiState.value.error
    }

    @Test
    fun `starts with an empty form`() {
        assertThat(ServerViewModel(repository).uiState.value).isEqualTo(ServerUiState())
    }

    @Test
    fun `a supported server navigates to login once`() {
        val viewModel = ServerViewModel(repository)

        viewModel.onAction(ServerAction.AddressChanged(" nas:8096 "))
        viewModel.onAction(ServerAction.Connect)

        assertThat(viewModel.uiState.value.nextKey).isEqualTo(OnboardingLoginKey("http://nas:8096", "Home"))
        assertThat(repository.checkedAddresses).containsExactly(" nas:8096 ")

        viewModel.onAction(ServerAction.NavigationHandled)
        assertThat(viewModel.uiState.value.nextKey).isNull()
    }

    @Test
    fun `each failed check has its own error`() {
        val old = ServerInfo(ServerId("s"), "Old", "10.11.6")
        assertThat(errorFor(ServerCheck.Unsupported(old))).isEqualTo(ServerError.Unsupported("10.11.6"))
        assertThat(errorFor(ServerCheck.InvalidAddress)).isEqualTo(ServerError.InvalidAddress)
        assertThat(errorFor(ServerCheck.CleartextNotAllowed)).isEqualTo(ServerError.CleartextNotAllowed)
        assertThat(errorFor(ServerCheck.Failed(MqError.Network))).isEqualTo(ServerError.Unreachable)
        assertThat(errorFor(ServerCheck.Failed(MqError.Unknown(Exception())))).isEqualTo(ServerError.NotJellyfin)
        assertThat(errorFor(ServerCheck.Failed(MqError.Server(500)))).isEqualTo(ServerError.Other)
    }

    @Test
    fun `editing the address clears the error`() {
        repository.serverCheck = ServerCheck.InvalidAddress
        val viewModel = ServerViewModel(repository)
        viewModel.onAction(ServerAction.AddressChanged("nas"))
        viewModel.onAction(ServerAction.Connect)

        viewModel.onAction(ServerAction.AddressChanged("nas:8096"))

        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `shows why the app signed out`() {
        repository.session.value = SessionState.LoggedOut(LogoutReason.SessionExpired)

        assertThat(ServerViewModel(repository).uiState.value.notice).isEqualTo(LogoutReason.SessionExpired)
    }
}
